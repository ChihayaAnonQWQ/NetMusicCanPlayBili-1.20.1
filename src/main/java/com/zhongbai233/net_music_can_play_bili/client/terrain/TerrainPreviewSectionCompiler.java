package com.zhongbai233.net_music_can_play_bili.client.terrain;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.zhongbai233.net_music_can_play_bili.terrain.core.TerrainFluidVertexCoordinates;
import com.zhongbai233.net_music_can_play_bili.terrain.core.TerrainPackedLight;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把不可变方块快照编译成原版 terrain 顶点格式，并按原版 chunk 渲染层
 * ({@link RenderType#solid()} / {@link RenderType#cutoutMipped()} / {@link RenderType#cutout()} /
 * {@link RenderType#translucent()}) 分层。
 *
 * <p>
 * 26.x 的 BlockStateModelSet/FluidStateModelSet/ChunkSectionLayer 在 1.21.1 不存在；这里改用
 * {@link BlockModelShaper} 烘焙模型 + {@link ModelBlockRenderer#tesselateBlock}（原版
 * SectionCompiler 同款管线，含真实 AO 与面剔除）+ {@link LiquidBlockRenderer} 流体，
 * 采光与 tint 取自不可变快照。模型/贴图来自当前资源包（shaper 在渲染线程捕获后传入）。
 * </p>
 */
public final class TerrainPreviewSectionCompiler {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(TerrainPreviewSectionCompiler.class);
    private static final int INITIAL_LAYER_BUFFER_BYTES = 256 * 1024;
    private static final RenderType[] LAYERS = {
        RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(), RenderType.translucent()};

    private TerrainPreviewSectionCompiler() {
    }

    public static CompiledCpuSection compile(TerrainPreviewFrame frame,
            TerrainBlockSectionSnapshot snapshot, BlockModelShaper modelShaper,
            BlockColors blockColors) {
        java.util.Objects.requireNonNull(modelShaper, "modelShaper");
        java.util.Objects.requireNonNull(blockColors, "blockColors");
        TerrainPreviewBlockAndTintGetter snapshotView = new TerrainPreviewBlockAndTintGetter(snapshot);
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(
                new FrozenTerrainBlockColors(snapshotView));
        LiquidBlockRenderer fluidRenderer = new LiquidBlockRenderer();
        // 1.20.1 移植说明：
        //  * 没有 ByteBufferBuilder —— 1.20.1 的 BufferBuilder 自带底层存储，
        //    故每层只保留一个 BufferBuilder（构造后需显式 begin）。
        //  * 没有 MeshData —— 用 BufferBuilder.RenderedBuffer 等价替代。
        //  * 没有「构建后重排半透明索引」的能力（MeshData#sortQuads /
        //    VertexBuffer#uploadIndexBuffer 都是 1.21 的 API），故半透明层的
        //    相机相关重排降级为 no-op（详见 TerrainPreviewGpuCache.GpuLayer）。
        BufferBuilder[] buildersByLayer = new BufferBuilder[LAYERS.length];
        Map<RenderType, BufferBuilder.RenderedBuffer> meshes = new HashMap<>();
        PoseStack poseStack = new PoseStack();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        boolean[] occupied = occupiedVoxels(snapshot);
        try {
            for (TerrainBlockSectionSnapshot.VisibleBlock block : snapshot.blocks()) {
                int worldX = snapshot.section().minBlockX() + block.localX();
                int worldY = snapshot.section().minBlockY() + block.localY();
                int worldZ = snapshot.section().minBlockZ() + block.localZ();
                if (worldX == frame.originX() && worldY == frame.originY() && worldZ == frame.originZ()) {
                    continue;
                }
                worldPos.set(worldX, worldY, worldZ);
                try {
                    if (block.cellSize() > 1) {
                        tesselateMaterialCell(block, snapshotView, modelShaper,
                                occupied, buildersByLayer);
                        continue;
                    }
                    FluidState fluidState = block.state().getFluidState();
                    if (!fluidState.isEmpty()) {
                        fluidRenderer.tesselate(snapshotView, worldPos,
                                sectionLocalFluidOutput(builderFor(RenderType.translucent(), buildersByLayer)),
                                block.state(), fluidState);
                    }
                    if (block.state().getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
                        BakedModel model = modelShaper.getBlockModel(block.state());
                        ChunkRenderTypeSet renderTypes = model.getRenderTypes(block.state(), random,
                                ModelData.EMPTY);
                        for (RenderType layer : renderTypes) {
                            RenderType target = compileLayer(layer);
                            if (target == null) {
                                if (layer == RenderType.tripwire()) {
                                    // 26.x 按材质透明度分类会把 translucent 材质归入 TRANSLUCENT；
                                    // 1.21.1 的 tripwire 同属半透明家族，映射到半透明层。
                                    target = RenderType.translucent();
                                } else {
                                    continue;
                                }
                            }
                            poseStack.pushPose();
                            poseStack.translate(block.localX() - 0.5F, block.localY(),
                                    block.localZ() - 0.5F);
                            try {
                                blockRenderer.tesselateBlock(snapshotView, model, block.state(), worldPos,
                                        poseStack, builderFor(target, buildersByLayer),
                                        true, random, block.state().getSeed(worldPos),
                                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, target);
                            } finally {
                                poseStack.popPose();
                            }
                        }
                    }
                } catch (Throwable incompatibleModBlock) {
                    if (incompatibleModBlock instanceof VirtualMachineError fatal) {
                        throw fatal;
                    }
                    if (incompatibleModBlock instanceof Error fatal) {
                        throw fatal;
                    }
                    LOGGER.warn("Skipping incompatible terrain preview block at ({}, {}, {}) in section {}",
                            worldX, worldY, worldZ, snapshot.section(), incompatibleModBlock);
                }
            }
            for (int i = 0; i < LAYERS.length; i++) {
                BufferBuilder builder = buildersByLayer[i];
                if (builder == null) {
                    continue;
                }
                // 1.20.1：build() -> endOrDiscardIfEmpty()；空缓冲会被丢弃并返回 null。
                BufferBuilder.RenderedBuffer mesh = builder.endOrDiscardIfEmpty();
                if (mesh != null) {
                    meshes.put(LAYERS[i], mesh);
                }
            }
            return new CompiledCpuSection(snapshot, meshes);
        } catch (Throwable failure) {
            meshes.values().forEach(mesh -> mesh.release());
            throw failure;
        }
    }

    /** 把任意 RenderType 归入 4 个持久层；不属于持久层且非 tripwire 的返回 null。 */
    private static RenderType compileLayer(RenderType layer) {
        for (RenderType candidate : LAYERS) {
            if (candidate == layer) {
                return candidate;
            }
        }
        return null;
    }

    private static int layerIndex(RenderType layer) {
        for (int i = 0; i < LAYERS.length; i++) {
            if (LAYERS[i] == layer) {
                return i;
            }
        }
        throw new IllegalArgumentException("unmapped terrain render layer: " + layer);
    }

    private static boolean[] occupiedVoxels(TerrainBlockSectionSnapshot snapshot) {
        boolean[] occupied = new boolean[16 * 16 * 16];
        for (TerrainBlockSectionSnapshot.VisibleBlock block : snapshot.blocks()) {
            for (int y = block.localY(); y < block.localY() + block.cellSize(); y++) {
                for (int z = block.localZ(); z < block.localZ() + block.cellSize(); z++) {
                    for (int x = block.localX(); x < block.localX() + block.cellSize(); x++) {
                        occupied[(y * 16 + z) * 16 + x] = true;
                    }
                }
            }
        }
        return occupied;
    }

    /** 大格子(map 悬浮块)只画外表面：取烘焙模型 particle 贴图当底图，按透明度定层。 */
    private static void tesselateMaterialCell(TerrainBlockSectionSnapshot.VisibleBlock block,
            TerrainPreviewBlockAndTintGetter snapshotView, BlockModelShaper modelShaper,
            boolean[] occupied, BufferBuilder[] buildersByLayer) {
        BakedModel model = modelShaper.getBlockModel(block.state());
        TextureAtlasSprite sprite = model.getParticleIcon(ModelData.EMPTY);
        RenderType layer;
        if (!block.state().getFluidState().isEmpty()) {
            layer = RenderType.translucent();
        } else {
            ChunkRenderTypeSet renderTypes = model.getRenderTypes(block.state(),
                    RandomSource.create(), ModelData.EMPTY);
            if (renderTypes.isEmpty() || renderTypes.contains(RenderType.translucent())) {
                layer = RenderType.translucent();
            } else if (renderTypes.contains(RenderType.solid())) {
                layer = RenderType.solid();
            } else if (renderTypes.contains(RenderType.cutoutMipped())) {
                layer = RenderType.cutoutMipped();
            } else {
                layer = RenderType.cutout();
            }
        }
        BufferBuilder output = builderFor(layer, buildersByLayer);
        // 1.20.1 没有 List#getFirst（Java 21 的 SequencedCollection）。
        int tint = block.tintLayers().isEmpty() ? -1 : block.tintLayers().get(0);
        if (tint != -1 && (tint & 0xFF000000) == 0) {
            tint |= 0xFF000000;
        }
        int light = LightTexture.pack(TerrainPackedLight.block(block.packedLight()),
                TerrainPackedLight.sky(block.packedLight()));
        for (Direction direction : Direction.values()) {
            if (faceExposed(block, direction, occupied)) {
                emitCellFace(output, block, direction, sprite, shade(tint, direction), light);
            }
        }
    }

    private static boolean faceExposed(TerrainBlockSectionSnapshot.VisibleBlock block,
            Direction direction, boolean[] occupied) {
        int x = block.localX() + direction.getStepX() * block.cellSize();
        int y = block.localY() + direction.getStepY() * block.cellSize();
        int z = block.localZ() + direction.getStepZ() * block.cellSize();
        if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
            return true;
        }
        return !occupied[(y * 16 + z) * 16 + x];
    }

    private static int shade(int color, Direction direction) {
        float factor = switch (direction) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
        int packedFactor = (int) (factor * 255.0F);
        return FastColor.ARGB32.multiply(color,
                FastColor.ARGB32.color(packedFactor, packedFactor, packedFactor, 255));
    }

    private static void emitCellFace(BufferBuilder output,
            TerrainBlockSectionSnapshot.VisibleBlock block, Direction direction,
            TextureAtlasSprite sprite, int color, int light) {
        float x0 = block.localX() - 0.5F;
        float y0 = block.localY();
        float z0 = block.localZ() - 0.5F;
        float x1 = x0 + block.cellSize();
        float y1 = y0 + block.cellSize();
        float z1 = z0 + block.cellSize();
        float[][] vertices = switch (direction) {
            case DOWN -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case WEST -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            case EAST -> new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
        };
        float[] u = {sprite.getU0(), sprite.getU1(), sprite.getU1(), sprite.getU0()};
        float[] v = {sprite.getV1(), sprite.getV1(), sprite.getV0(), sprite.getV1()};
        var normal = direction.step();
        for (int index = 0; index < 4; index++) {
            // 1.20.1 没有 1.21 的 11 参 addVertex(x,y,z,color,u,v,overlay,light,nx,ny,nz) 便捷重载，
            // 按本层实际格式逐元素展开。本工程的四个地形层都是原版 solid/cutoutMipped/cutout/translucent，
            // 1.20.1 它们的格式是 DefaultVertexFormat.BLOCK =
            // POSITION,COLOR,UV0,UV2,NORMAL,PADDING（<b>没有 UV1/overlay</b>，1.21 才多出 overlay），
            // 所以这里不能写 overlayCoords（会抛 "Overlay is not the current element"），
            // 且末尾必须显式 endVertex()（1.20.1 只在 endVertex() 里递增顶点计数）。
            output.vertex(vertices[index][0], vertices[index][1], vertices[index][2])
                    .color(color)
                    .uv(u[index], v[index])
                    .uv2(light)
                    .normal(normal.x(), normal.y(), normal.z())
                    .endVertex();
        }
    }

    /** Supplies frozen per-position tint values without invoking mod or level code on the compiler thread. */
    private static final class FrozenTerrainBlockColors extends BlockColors {
        private final TerrainPreviewBlockAndTintGetter view;

        private FrozenTerrainBlockColors(TerrainPreviewBlockAndTintGetter view) {
            this.view = view;
        }

        @Override
        public int getColor(BlockState state, BlockAndTintGetter level,
                BlockPos pos, int tintIndex) {
            if (level == view && pos != null) {
                int tint = view.precomputedTint(pos, tintIndex);
                if (tint != -1) {
                    return tint;
                }
            }
            return -1;
        }
    }

    /**
     * 取（或创建并 begin）某个渲染层的顶点缓冲。
     *
     * <p>1.20.1 移植说明：1.21 的 {@code new BufferBuilder(ByteBufferBuilder, Mode, VertexFormat)}
     * 是"构造即开始写入"；1.20.1 对应 {@code new BufferBuilder(capacity)} +
     * {@code begin(mode, format)}，构造后必须显式 begin，且只做一次。</p>
     */
    private static BufferBuilder builderFor(RenderType layer, BufferBuilder[] builders) {
        int index = layerIndex(layer);
        BufferBuilder builder = builders[index];
        if (builder == null) {
            builder = new BufferBuilder(INITIAL_LAYER_BUFFER_BYTES);
            builder.begin(VertexFormat.Mode.QUADS, layer.format());
            builders[index] = builder;
        }
        return builder;
    }

    static VertexConsumer sectionLocalFluidOutput(VertexConsumer delegate) {
        return new TranslatedVertexConsumer(delegate,
                TerrainFluidVertexCoordinates.offsetX(),
                TerrainFluidVertexCoordinates.offsetY(),
                TerrainFluidVertexCoordinates.offsetZ());
    }

    private static final class TranslatedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float offsetX;
        private final float offsetY;
        private final float offsetZ;

        private TranslatedVertexConsumer(VertexConsumer delegate,
                float offsetX, float offsetY, float offsetZ) {
            this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x + offsetX, y + offsetY, z + offsetZ);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        /** 1.21 的 setUv1(int,int) 就是 1.20.1 的 overlayCoords(int,int)（UV1 元素）。 */
        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        /** 1.21 的 setUv2(int,int) 就是 1.20.1 的 uv2(int,int)（UV2 = 光照贴图元素）。 */
        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    /**
     * 编译产物：每层一个已 end 的顶点缓冲。
     *
     * <p><b>1.20.1 降级说明</b>：1.21 的 {@code CompiledCpuSection} 还持有「每层的
     * ByteBufferBuilder 存储」与「半透明层的 {@code MeshData.SortState}」，用于在相机移动时
     * <b>只重排索引</b>而不重建顶点。1.20.1 <b>没有这些 API</b>：{@code MeshData} 不存在，
     * {@code BufferBuilder.SortState} 没有任何公开方法（构造器也是包私有），
     * {@code VertexBuffer#uploadIndexBuffer} 也不存在。故这里只保留顶点缓冲，
     * 半透明层的相机相关重排随之降级（见 {@code TerrainPreviewGpuCache.GpuLayer}）。</p>
     */
    public record CompiledCpuSection(TerrainBlockSectionSnapshot source,
            Map<RenderType, BufferBuilder.RenderedBuffer> layers) implements AutoCloseable {
        public CompiledCpuSection {
            java.util.Objects.requireNonNull(source, "source");
            layers = new HashMap<>(java.util.Objects.requireNonNull(layers, "layers"));
        }

        @Override
        public void close() {
            for (BufferBuilder.RenderedBuffer mesh : layers.values()) {
                try {
                    mesh.release();
                } catch (IllegalStateException alreadyReleased) {
                    // 1.21 的 MeshData#close 是幂等的，但 1.20.1 的 RenderedBuffer#release
                    // 二次调用会抛 IllegalStateException("Buffer has already been released!")，
                    // 而 VertexBuffer#upload 内部已经调用过 release（反汇编确证：upload 的
                    // 正常与异常路径各有一处 invokevirtual RenderedBuffer.release）。
                    // 即"已被 upload 接管"正是这里要容忍的情况。
                }
            }
            layers.clear();
        }
    }
}