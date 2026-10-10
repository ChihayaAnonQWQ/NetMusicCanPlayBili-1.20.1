package com.zhongbai233.net_music_can_play_bili.block;

import com.zhongbai233.net_music_can_play_bili.blockentity.VideoProjectorBlockEntity;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.link.LinkHelper;
import com.zhongbai233.net_music_can_play_bili.item.MediaManagementToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VideoProjectorBlock extends Block implements EntityBlock {
    /**
     * 设备朝向。用全部六个方向而不是 HORIZONTAL_FACING：旧存档里已经存在 {@code facing=up}，
     * 收窄取值域会让存档重新解析方块状态时失败。放置时写入水平方向，上下仅作历史兼容。
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");
    private static final VoxelShape SHAPE = Block.box(2.75, 0, 2.75, 13.25, 5.3, 13.25);

    public VideoProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(ACTIVATED, false));
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVATED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 与原版熔炉一致：正面朝向放置者。这样模型朝向与投射面法线同向（投射面也是正对放置者）。
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 旋转/镜像时保持朝向语义（与原版熔炉等朝向方块一致）。 */
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VideoProjectorBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            applyPlacementFacing(level, pos, placer);
            applyLinkedPosition(level, pos, stack, placer);
        }
    }

    /**
     * 放置者的水平朝向 → 投射面 yaw。
     *
     * <p>渲染侧屏幕法线为 {@code (sin yaw, 0, cos yaw)}：yaw 180° 时法线朝北，与方块实体
     * {@code projectionYaw} 的既有默认值完全一致。要让屏幕正对放置者，法线必须指向其视线的
     * 反方向，于是映射为 南→180°、东→270°、北→0°、西→90°。</p>
     *
     * <p><b>不能直接用 {@code Direction#toYRot()}：</b>那是实体 yaw 约定（南0/西90/北180/东270），
     * 与渲染侧在东西两个方向上符号相反。实测正是"东西正确、南北反了"——因为两套约定在 0°/180°
     * 上取值相同，只在 90°/270° 上相差一个符号。</p>
     */
    private static float placementYaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 180.0F;
        };
    }

    /**
     * 让投射面正对放置者。
     *
     * <p>方块实体里 {@code projectionYaw} 默认 180°（法线朝北），所以此前不论从哪个方向放置，
     * 屏幕都固定朝北。这里按放置者的水平视线写入初始值，只吸附四个正方向便于对齐建筑；玩家之后
     * 仍可在配置界面的 yaw 滑条（0–360°）上任意微调。放置者可能为空（发射器），此时保留默认朝向。</p>
     */
    private static void applyPlacementFacing(Level level, BlockPos pos, LivingEntity placer) {
        if (placer == null) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof VideoProjectorBlockEntity projector) {
            projector.setProjectionYaw(placementYaw(placer.getDirection()));
            // setProjectionYaw 只调 setChanged()，跨端还要靠方块实体更新包；否则客户端在区块重载前
            // 仍按默认 180° 渲染，表现为"放下时朝向没变"。
            projector.markDirtyAndSync();
        }
    }

    /** 从物品 NBT 读取远程连接目标并写入视频投影仪方块实体 */
    private static void applyLinkedPosition(Level level, BlockPos pos, ItemStack stack, LivingEntity placer) {
        BlockPos linkedPos = LinkHelper.readLinkFromItem(stack);
        if (linkedPos == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof VideoProjectorBlockEntity projector) {
            projector.linkTo(linkedPos);
            if (!(placer instanceof Player player) || !player.isCreative()) {
                LinkHelper.clearLinkFromItem(stack);
                clearLinkedBlockEntityData(stack);
            }
        }
    }

    /**
     * 同步写入标准方块实体数据。MinecartRevolution 等通用方块载具会复制该数据，
     * 因而无需依赖本模组的物品 CUSTOM_DATA 即可保留唱片机链接。
     *
     * <p><b>1.20.1 移植说明</b>：1.21.1 用数据组件 {@code DataComponents.BLOCK_ENTITY_DATA}
     * （{@code CustomData}）承载这份数据；1.20.1 没有数据组件，其等价物是物品 NBT 里的
     * {@code "BlockEntityTag"} 复合标签，也正是原版 {@code BlockItem#setBlockEntityData} /
     * {@code BlockItem#getBlockEntityData} 读写的那一项。故本方法把三处组件调用逐一映射：
     * {@code stack.get(DataComponents.BLOCK_ENTITY_DATA)} →
     * {@code stack.getTagElement("BlockEntityTag")}（返回可空的 {@code CompoundTag}）；
     * {@code stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag))} →
     * {@code stack.getOrCreateTag().put("BlockEntityTag", tag)}；
     * {@code stack.remove(DataComponents.BLOCK_ENTITY_DATA)} →
     * {@code stack.removeTagKey("BlockEntityTag")}。</p>
     *
     * <p><b>必须补 id:</b>1.20.1 的 {@code BlockEntityTag} 同样要求 NBT 含字符串类型的
     * {@code id}（方块实体类型），否则放置时方块实体无法确定类型。
     * 从创造栏取出的新物品没有该数据，故这里手工补写。
     * 未改用原版 {@code BlockItem#setBlockEntityData}：它内部调
     * {@code BlockEntity#addEntityType} 无条件覆盖 id，会破坏"不覆盖别处写好的 id"这一约定。</p>
     */
    public static void writeLinkedBlockEntityData(ItemStack stack, BlockPos linkedPos) {
        CompoundTag existing = stack.getTagElement("BlockEntityTag");
        CompoundTag tag = existing != null ? existing.copy() : new CompoundTag();
        ensureBlockEntityId(tag);
        tag.putBoolean("LinkedTarget_has", true);
        tag.putInt("LinkedTarget_x", linkedPos.getX());
        tag.putInt("LinkedTarget_y", linkedPos.getY());
        tag.putInt("LinkedTarget_z", linkedPos.getZ());
        stack.getOrCreateTag().put("BlockEntityTag", tag);
    }

    /**
     * 保证 {@code BlockEntityTag} 携带方块实体类型 id。
     * 只在本模组投影仪自身的数据里补写,不会覆盖别处写好的 id。
     */
    private static void ensureBlockEntityId(CompoundTag tag) {
        if (tag.contains("id", Tag.TAG_STRING)) {
            return;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(ModBlockEntities.VIDEO_PROJECTOR.get());
        if (key != null) {
            tag.putString("id", key.toString());
        }
    }

    /** 清除标准方块实体数据（1.20.1：物品 NBT 的 {@code "BlockEntityTag"}）中的唱片机链接，同时保留投影参数等其它数据。 */
    public static void clearLinkedBlockEntityData(ItemStack stack) {
        CompoundTag existing = stack.getTagElement("BlockEntityTag");
        if (existing == null) {
            return;
        }
        CompoundTag tag = existing.copy();
        tag.remove("LinkedTarget_has");
        tag.remove("LinkedTarget_x");
        tag.remove("LinkedTarget_y");
        tag.remove("LinkedTarget_z");
        if (tag.isEmpty()) {
            stack.removeTagKey("BlockEntityTag");
            return;
        }
        ensureBlockEntityId(tag);
        stack.getOrCreateTag().put("BlockEntityTag", tag);
    }

    /**
     * 方块右键交互。
     *
     * <p><b>1.20.1 移植说明</b>：1.20.5+ 拆成 {@code useItemOn} 与 {@code useWithoutItem} 两个入口
     * （后者在前者返回 {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} 时被原版调用），
     * 而 1.20.1 只有 {@code use(...)} 一个入口。故合并：原来返回
     * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} 的两条分支，直接展开为原
     * {@code useWithoutItem} 的行为。</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if ((hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof MediaManagementToolItem)
                || !player.mayBuild()) {
            return openScreenOrPass(level, pos, player);
        }
        if (level.isClientSide()) {
            openClientScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** 原 1.21.1 {@code useWithoutItem} 的等价实现：不能建造则 PASS，否则（客户端）打开界面。 */
    private static InteractionResult openScreenOrPass(Level level, BlockPos pos, Player player) {
        if (!player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            openClientScreen(pos);
        }
        return InteractionResult.SUCCESS;
    }

    private static void openClientScreen(BlockPos pos) {
        com.zhongbai233.net_music_can_play_bili.client.VideoProjectorClient.openScreen(pos);
    }
}
