package com.zhongbai233.net_music_can_play_bili.port.shim;

import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * {@link RenderStateShard} 状态常量的访问转发（1.20.1 权限降级用）。
 *
 * <p><b>为什么需要它：</b>1.21 把这些状态常量与 {@code LineStateShard} 都改成了 {@code public}，
 * 而 1.20.1 里它们仍是 {@code protected static}：
 * {@code TRANSLUCENT_TRANSPARENCY}/{@code NO_CULL}/{@code COLOR_WRITE}/
 * {@code COLOR_DEPTH_WRITE}/{@code VIEW_OFFSET_Z_LAYERING}/{@code RENDERTYPE_LINES_SHADER}，
 * 嵌套类 {@code LineStateShard} 本身也是 {@code protected static} —— 包外既取不到常量、
 * 也无法 {@code new LineStateShard(...)}，编译报 "has protected access in RenderStateShard"。</p>
 *
 * <p><b>做法：</b>本类继承 {@link RenderStateShard}（子类体内可以合法访问这些 protected 静态成员），
 * 只把它们以「应用到 {@link RenderType.CompositeState.CompositeStateBuilder}」的形式透出。
 * 返回类型统一用 public 的 {@code CompositeStateBuilder}，不让 protected 类型泄漏到调用点。</p>
 *
 * <p><b>边界：</b>纯粹是访问权限的转发，<b>不增删任何状态、不改变任何语义</b>；
 * 调用点用到的状态与 1.21 版本逐项一一对应。</p>
 */
public final class PortRenderStateShards extends RenderStateShard {
    private PortRenderStateShards() {
        // 本类只作为 protected 静态成员的可访问入口，不会被实例化。
        super("ncpb_port_render_state_shards", () -> {
        }, () -> {
        });
    }

    /** 追加 {@code TRANSLUCENT_TRANSPARENCY}。 */
    public static RenderType.CompositeState.CompositeStateBuilder translucentTransparency(
            RenderType.CompositeState.CompositeStateBuilder builder) {
        return builder.setTransparencyState(TRANSLUCENT_TRANSPARENCY);
    }

    /** 追加 {@code NO_CULL}。 */
    public static RenderType.CompositeState.CompositeStateBuilder noCull(
            RenderType.CompositeState.CompositeStateBuilder builder) {
        return builder.setCullState(NO_CULL);
    }

    /** 追加 {@code COLOR_WRITE}（只写颜色，不写深度）。 */
    public static RenderType.CompositeState.CompositeStateBuilder colorWrite(
            RenderType.CompositeState.CompositeStateBuilder builder) {
        return builder.setWriteMaskState(COLOR_WRITE);
    }

    /** 追加 {@code COLOR_DEPTH_WRITE}（颜色与深度都写）。 */
    public static RenderType.CompositeState.CompositeStateBuilder colorDepthWrite(
            RenderType.CompositeState.CompositeStateBuilder builder) {
        return builder.setWriteMaskState(COLOR_DEPTH_WRITE);
    }

    /** 追加 {@code TRANSLUCENT_TRANSPARENCY} + {@code NO_CULL}。 */
    public static RenderType.CompositeState.CompositeStateBuilder translucentNoCull(
            RenderType.CompositeState.CompositeStateBuilder builder) {
        return noCull(translucentTransparency(builder));
    }

    /**
     * 完整复刻 1.21 {@code RenderType.linesTranslucent()} 的状态集：
     * {@code RENDERTYPE_LINES_SHADER} + 指定像素宽度的 {@code LineStateShard}
     * + {@code VIEW_OFFSET_Z_LAYERING} + {@code TRANSLUCENT_TRANSPARENCY}
     * + {@code COLOR_DEPTH_WRITE} + {@code NO_CULL}。
     */
    public static RenderType.CompositeState translucentLinesState(double lineWidth) {
        return RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.of(lineWidth)))
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .setCullState(NO_CULL)
                .createCompositeState(false);
    }
}
