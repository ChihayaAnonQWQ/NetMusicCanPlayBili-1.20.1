package com.zhongbai233.net_music_can_play_bili.port.shim;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Blits an offscreen framebuffer color texture into the GUI.
 *
 * <p>{@link GuiGraphics#blit} uses the sprite convention where texture row {@code v=0} is the
 * image top. OpenGL framebuffer attachments use the opposite convention (row {@code v=0} is the
 * rendered bottom), so a plain {@code blit} would show framebuffer content upside down. This
 * helper draws the quad directly with inverted v coordinates (destination top samples {@code v=1}),
 * preserving the scene orientation.</p>
 */
public final class PortGuiFramebufferBlitter {
    private PortGuiFramebufferBlitter() {
    }

    public static void blit(GuiGraphics guiGraphics, int textureId, int x, int y, int width, int height) {
        if (textureId < 0 || width <= 0 || height <= 0) {
            return;
        }
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = guiGraphics.pose().last().pose();
        float x1 = x;
        float y1 = y;
        float x2 = x + (float) width;
        float y2 = y + (float) height;
        // 1.20.1：Tesselator#begin(...) 不存在，改为 getBuilder() + 对同一 builder begin(...)；
        // buildOrThrow() 不存在，等价物是 end()。
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // v flipped: dest top-left samples the framebuffer's top row (v=1), bottom-left its bottom row (v=0).
        // 顶点链按 1.20.1 名称重写并补显式 endVertex()。
        builder.vertex(matrix, x1, y1, 0.0F).uv(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        builder.vertex(matrix, x1, y2, 0.0F).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        builder.vertex(matrix, x2, y2, 0.0F).uv(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        builder.vertex(matrix, x2, y1, 0.0F).uv(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }
}