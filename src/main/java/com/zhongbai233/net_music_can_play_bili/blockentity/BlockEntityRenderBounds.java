package com.zhongbai233.net_music_can_play_bili.blockentity;

import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElement;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElementPosition;
import com.zhongbai233.net_music_can_play_bili.util.NcpbSystemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * 四个方块实体的渲染裁剪包围盒：**公共（非客户端）**的唯一实现。
 *
 * <p><b>1.20.1 移植说明（钩子位置）</b>：1.21 的 Forge 把 {@code getRenderBoundingBox(T)} 定义在
 * {@code IForgeBlockEntityRenderer} 上，所以 {@code client/renderer/} 里四个 BER 的同名方法原本是覆写。
 * 1.20.1 的 {@code BlockEntityRenderer} <b>没有</b>这个方法（javap 已确证），Forge 的钩子在
 * {@code IForgeBlockEntity#getRenderBoundingBox()}——挂在 <b>BlockEntity</b> 上
 * （{@code BlockEntity implements IForgeBlockEntity}，javap 实证 {@code public default AABB getRenderBoundingBox()}）。
 * 也就是说：真正被原版裁剪读取的是<b>方块实体</b>上的覆写，四个 BER 里的同名方法已不再是覆写、只剩内部调用。
 * 本类把那段计算搬到公共侧，BE 与 BER 都能调用，避免两边各写一份几何。</p>
 *
 * <p><b>为什么放在 {@code blockentity} 包</b>：这里是专用服务端也会加载的公共类，只能依赖
 * 公共 API。本类只用到 {@link BlockPos}/{@link AABB}/{@code org.joml.*}/
 * {@code editor.host.controlconsole.document.*}/{@link NcpbSystemProperties}，<b>没有</b>任何
 * {@code net.minecraft.client.*} 或 {@code client.renderer.*} 依赖，所以服务端加载它不会牵出客户端类。</p>
 *
 * <p><b>盒子语义来源</b>（逐行对齐，公式未做任何"近似"）：
 * <ul>
 *   <li>{@link #turntable} ← {@code ModernTurntableRenderer:137}</li>
 *   <li>{@link #lyricProjector} ← {@code LyricProjectorRenderer:348-360}</li>
 *   <li>{@link #videoProjector}/{@link #projectorScreen} ← {@code VideoProjectorRenderer:178-189}
 *       + {@code ProjectorScreenBounds.aroundBlock} + {@code ProjectorScreenGeometry.aroundCenter}</li>
 *   <li>{@link #controlConsole} ← {@code ControlConsoleRenderer:386-435}</li>
 * </ul>
 * <b>已知差异（视频投影仪的画面宽高比）</b>：BER 版的宽高比来自
 * {@code VideoBillboardPreview.currentProjectorDisplayFrame(pos)}（客户端解码器的运行时帧状态，服务端不可得），
 * 无帧时退回 16:9。BE 侧只能用 16:9 默认值；需要真实宽高比时由客户端调用
 * {@link #videoProjector(BlockPos, double, double, double, double, double, double, double)} 显式传入。
 * 详见该方法注释。</p>
 */
public final class BlockEntityRenderBounds {
    /** 投影面基准高度，对应 {@code ProjectorScreenGeometry.BASE_HEIGHT}。 */
    public static final double PROJECTOR_BASE_HEIGHT = 1.35D;
    /** BER 在没有可用视频帧时使用的默认画面宽高比（{@code VideoProjectorRenderer:182}）。 */
    public static final double DEFAULT_VIDEO_ASPECT = 16.0D / 9.0D;
    /** BER 对宽高比的下限钳制（{@code VideoProjectorRenderer:183}）。 */
    private static final double MIN_VIDEO_ASPECT = 1.0D / 8.0D;

    /**
     * 歌词投影排版常量，必须与 {@code client/renderer/LyricProjectorRenderer} 里的
     * {@code TEXT_SCALE=0.025F}、{@code LINE_STEP=14.0F}、{@code VISIBLE_LINES_ABOVE=2}、
     * {@code VISIBLE_LINES_BELOW=2} 保持一致（那四个是渲染器私有常量，公共侧只能重声明）。
     * 改动排版常量时两处必须同步。
     */
    private static final double LYRIC_TEXT_SCALE = 0.025D;
    private static final double LYRIC_LINE_STEP = 14.0D;
    private static final int LYRIC_VISIBLE_LINES_ABOVE = 2;
    private static final int LYRIC_VISIBLE_LINES_BELOW = 2;

    /**
     * JVM 属性键与默认值，对应 {@code client/renderer/ProjectorRenderProperties}
     * （那是包私有客户端类，公共侧只能重声明键名与默认值）。与 BER 一样在静态初始化时读一次。
     */
    private static final double VIDEO_MARGIN =
            Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.video.projector.render_margin", 0.5D));
    private static final double VIDEO_MAX_ASPECT =
            Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.video.projector.render_max_aspect", 8.0D));
    private static final double LYRIC_MARGIN =
            Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.lyric.projector.render_margin", 2.0D));
    private static final double LYRIC_MIN_INFLATE =
            Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.lyric.projector.render_min_inflate", 2.5D));
    private static final double LYRIC_MAX_TEXT_WIDTH =
            Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.lyric.projector.render_max_text_width", 8.0D));

    private BlockEntityRenderBounds() {
    }

    /** 现代唱片机：盒子固定为方块周围 1/2.5/1 格（{@code ModernTurntableRenderer:137}）。 */
    public static AABB turntable(BlockPos pos) {
        return new AABB(pos).inflate(1.0D, 2.5D, 1.0D);
    }

    /**
     * 歌词投影仪：以方块为中心、按「投影偏移半径 + 文本半径 + 余量」膨胀
     * （{@code LyricProjectorRenderer:348-360} 的逐行等价实现）。
     *
     * @param projectionScale  {@code LyricProjectorBlockEntity#getProjectionScale()}
     * @param distanceX        {@code LyricProjectorBlockEntity#getProjectionDistanceX()}
     * @param height           {@code LyricProjectorBlockEntity#getProjectionHeight()}
     * @param distanceZ        {@code LyricProjectorBlockEntity#getProjectionDistanceZ()}
     */
    public static AABB lyricProjector(BlockPos pos, double projectionScale, double distanceX,
            double height, double distanceZ) {
        double scale = Math.max(0.25D, Math.abs(projectionScale));
        double textHalfWidth = LYRIC_MAX_TEXT_WIDTH * scale * 0.5D;
        double lineHeight = LYRIC_LINE_STEP * LYRIC_TEXT_SCALE * scale;
        double textHalfHeight = Math.max(lineHeight,
                lineHeight * (1 + LYRIC_VISIBLE_LINES_ABOVE + LYRIC_VISIBLE_LINES_BELOW) * 0.5D);
        double textRadius = Math.sqrt(textHalfWidth * textHalfWidth + textHalfHeight * textHalfHeight);
        double offsetRadius = Math.sqrt(distanceX * distanceX + height * height + distanceZ * distanceZ);
        double inflate = Math.max(LYRIC_MIN_INFLATE, offsetRadius + textRadius + LYRIC_MARGIN);
        return new AABB(pos).inflate(inflate, inflate, inflate);
    }

    /**
     * 视频投影仪（BE 侧默认宽高比）：{@code VideoProjectorRenderer:178-189} 的等价物，
     * 宽高比取 {@link #DEFAULT_VIDEO_ASPECT}（即 BER 在"没有可用视频帧"时的取值）。
     *
     * <p><b>不确定点</b>：BER 在客户端会先用解码器当前帧的 width/height 推导真实宽高比，
     * 那是只有客户端渲染期才知道的状态（{@code VideoBillboardPreview} 是 client 包类，BE 不能引用）。
     * 因此当屏幕上正在播放非 16:9 画面时，这个裁剪盒在水平方向会比 BER 自己的盒子窄
     * （真实宽高比 / 16:9 倍，最高到 {@link #videoMaxAspect()} 倍的钳制上限），
     * 极端宽幅画面下仍可能被提前裁剪。要消除该差异，需客户端在渲染时调用重载
     * {@link #videoProjector(BlockPos, double, double, double, double, double, double, double)} 传入真实宽高比。</p>
     */
    public static AABB videoProjector(BlockPos pos, double offsetX, double height, double offsetZ,
            double yawDegrees, double pitchDegrees, double scale) {
        return videoProjector(pos, offsetX, height, offsetZ, yawDegrees, pitchDegrees, scale,
                DEFAULT_VIDEO_ASPECT);
    }

    /** 视频投影仪（显式宽高比）：宽高比按 BER 同样规则钳制到 [1/8, maxAspect] 后套用余量。 */
    public static AABB videoProjector(BlockPos pos, double offsetX, double height, double offsetZ,
            double yawDegrees, double pitchDegrees, double scale, double aspect) {
        return projectorScreen(pos, offsetX, height, offsetZ, yawDegrees, pitchDegrees, scale,
                clampVideoAspect(aspect), videoMargin());
    }

    /**
     * 旋转后投影四边形的世界 AABB：与 {@code client/renderer/ProjectorScreenBounds#aroundBlock}
     * （底层 {@code ProjectorScreenGeometry#aroundCenter}）逐行等价的纯几何实现，供 BE 与 BER 共用。
     */
    public static AABB projectorScreen(BlockPos blockPos, double offsetX, double height, double offsetZ,
            double yawDegrees, double pitchDegrees, double scale, double aspect, double margin) {
        double centerX = blockPos.getX() + 0.5D + offsetX;
        double centerY = blockPos.getY() + height;
        double centerZ = blockPos.getZ() + 0.5D + offsetZ;
        double halfHeight = PROJECTOR_BASE_HEIGHT * Math.abs(scale) * 0.5D;
        double halfWidth = halfHeight * Math.max(0.0D, aspect);
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double upX = forwardX * Math.sin(pitch);
        double upY = Math.cos(pitch);
        double upZ = forwardZ * Math.sin(pitch);

        double extentX = Math.abs(rightX * halfWidth) + Math.abs(upX * halfHeight);
        double extentY = Math.abs(upY * halfHeight);
        double extentZ = Math.abs(rightZ * halfWidth) + Math.abs(upZ * halfHeight);
        double safeMargin = Math.max(0.0D, margin);
        return new AABB(centerX - extentX - safeMargin, centerY - extentY - safeMargin,
                centerZ - extentZ - safeMargin, centerX + extentX + safeMargin,
                centerY + extentY + safeMargin, centerZ + extentZ + safeMargin);
    }

    /**
     * 中控台：1 格基础体积 ∪ 每个启用元素的包围盒
     * （{@code ControlConsoleRenderer:386-435} 的逐行等价实现；AUDIO 元素取元素世界原点）。
     *
     * <p>与 BER 原实现一致：消费者范围（tickConsumers 维护的 64 格听众范围）<b>不</b>计入裁剪盒，
     * 否则离屏中控台会被误判为可见。1.55 的屏幕基准高度取自
     * {@link ControlConsoleElementPosition#CONSOLE_ELEMENT_BASE_Y}（BER 里是字面量 1.55D，同值）。</p>
     */
    public static AABB controlConsole(BlockPos pos, ControlConsoleDocument document) {
        if (document == null) {
            return new AABB(pos);
        }
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = pos.getX() + 1.0D;
        double maxY = pos.getY() + 1.0D;
        double maxZ = pos.getZ() + 1.0D;
        for (ControlConsoleElement element : document.elements()) {
            if (!element.enabled()) {
                continue;
            }
            Matrix4f transform = element.editorTransform().matrix();
            if (element.type() == ControlConsoleElement.Type.AUDIO) {
                Vector3d worldPosition = ControlConsoleElementPosition.worldPosition(
                        pos.getX(), pos.getY(), pos.getZ(), element);
                minX = Math.min(minX, worldPosition.x);
                minY = Math.min(minY, worldPosition.y);
                minZ = Math.min(minZ, worldPosition.z);
                maxX = Math.max(maxX, worldPosition.x);
                maxY = Math.max(maxY, worldPosition.y);
                maxZ = Math.max(maxZ, worldPosition.z);
                continue;
            }
            float halfHeight = element.height() * 0.5F;
            float halfWidth = halfHeight * element.aspect();
            for (int ySign : new int[] { -1, 1 }) {
                for (int xSign : new int[] { -1, 1 }) {
                    Vector3f corner = transform.transformPosition(
                            new Vector3f(xSign * halfWidth, ySign * halfHeight, 0.0F));
                    double worldX = centerX + corner.x;
                    double worldY = pos.getY() + ControlConsoleElementPosition.CONSOLE_ELEMENT_BASE_Y + corner.y;
                    double worldZ = centerZ + corner.z;
                    minX = Math.min(minX, worldX);
                    minY = Math.min(minY, worldY);
                    minZ = Math.min(minZ, worldZ);
                    maxX = Math.max(maxX, worldX);
                    maxY = Math.max(maxY, worldY);
                    maxZ = Math.max(maxZ, worldZ);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 点到 AABB 的最近距离平方（点在盒内为 0）：与
     * {@code client/renderer/ProjectorScreenBounds#distanceToSqr(AABB, Vec3)} 等价，
     * 供 BER 的 {@code shouldRender} 距离判断共用。
     */
    public static double distanceToSqr(AABB bounds, Vec3 point) {
        double dx = axisDistance(point.x, bounds.minX, bounds.maxX);
        double dy = axisDistance(point.y, bounds.minY, bounds.maxY);
        double dz = axisDistance(point.z, bounds.minZ, bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** BER 使用的视频包围盒余量（JVM 属性，默认 0.5）。 */
    public static double videoMargin() {
        return VIDEO_MARGIN;
    }

    /** BER 使用的画面宽高比上限（JVM 属性，默认 8.0）。 */
    public static double videoMaxAspect() {
        return VIDEO_MAX_ASPECT;
    }

    /** 按 BER 的规则钳制画面宽高比：{@code min(maxAspect, max(1/8, aspect))}。 */
    public static double clampVideoAspect(double aspect) {
        return Math.min(VIDEO_MAX_ASPECT, Math.max(MIN_VIDEO_ASPECT, aspect));
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        return value > max ? value - max : 0.0D;
    }
}
