package com.zhongbai233.net_music_can_play_bili.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zhongbai233.net_music_can_play_bili.media.VideoSurfaceBrightness;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Common helpers for small custom quads emitted through Minecraft's vertex API.
 */
public final class RenderVertexUtils {
    public static final int FULL_BRIGHT = 0x00F000F0;

    private RenderVertexUtils() {
    }

    public static void texturedVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
            float u, float v) {
        texturedVertex(buffer, pose, x, y, z, u, v, 1.0F);
    }

    public static void texturedVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
            float u, float v, float opacity) {
        texturedVertex(buffer, pose, x, y, z, u, v, opacity, VideoSurfaceBrightness.DEFAULT);
    }

    /**
     * 发射一个平面显示面的顶点。
     *
     * <p><b>法线为什么必须是世界空间的定值：</b>原实现在局部空间写 {@code (0,0,1)} 并经 pose 变换，
     * 于是屏幕法线会随朝向变成 +X/−Z 等不同世界方向。MC 的实体着色器会用法线做 cardinal lighting
     * （原版方块面明暗：上 1.0、南北 0.8、东西 0.6），结果同一块屏幕**朝东西比朝南北暗**——实测
     * 两屏亮度比为 0.68~0.70，恰好对应 0.6 / 0.8。手机/掌机屏幕更糟：玩家一转身亮度就会变。</p>
     *
     * <p>显示面应当是照度无关的平整发光面，因此这里直接写世界空间向上的法线（不经 pose，yaw 与
     * pitch 都无法改变它），使所有朝向取到同一个最高照明因子。光照本身仍是
     * {@link #FULL_BRIGHT}，颜色仍由亮度参数决定，所以观感只变得"各朝向一致且不再被压暗"。</p>
     */
    public static void texturedVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
            float u, float v, float opacity, float brightness) {
        // 1.20.1 的 VertexConsumer 没有 1.21 的 addVertex(...)/setXxx(...) 链式 API，等价映射为：
        //   addVertex(Pose, x, y, z)   -> vertex(pose.pose(), x, y, z)   (1.21 的 addVertex(Pose,...) 就是它)
        //   addVertex(Matrix4f, ...)   -> vertex(matrix, x, y, z)        (同为 default 方法, Vector4f.transform)
        //   setColor -> color | setUv -> uv | setOverlay -> overlayCoords | setLight -> uv2 | setNormal -> normal
        // 另外 1.20.1 必须显式 endVertex() 收尾：1.21 由下一次 addVertex 隐式结束顶点，
        // 1.20.1 的 BufferBuilder 只在 endVertex() 里递增顶点计数（否则顶点数为 0，画不出东西）。
        buffer.vertex(pose.pose(), x, y, z)
                .color(VideoSurfaceBrightness.vertexColor(brightness, opacity))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}