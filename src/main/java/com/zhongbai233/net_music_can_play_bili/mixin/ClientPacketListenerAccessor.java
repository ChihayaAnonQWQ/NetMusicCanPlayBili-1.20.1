package com.zhongbai233.net_music_can_play_bili.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link ClientPacketListener} 的私有 {@code level} 字段。
 *
 * <p><b>为什么不能用 {@code @Shadow}（实机实证）</b>：本类原先在
 * {@code ClientPacketListenerBlockUpdateMixin} 里用 {@code @Shadow private ClientLevel level;}。
 * 实机日志（2026-10-10 14:20:11）报：
 * <pre>
 * Mixin apply failed net_music_can_play_bili.mixins.json:ClientPacketListenerBlockUpdateMixin
 *   -&gt; net.minecraft.client.multiplayer.ClientPacketListener:
 *   InvalidMixinException @Shadow field level was not located in the target class
 *   ... Using refmap mixin.refmap.json
 * MixinApplyError ... FAILED during APPLY   ← 客户端启动崩溃
 * </pre>
 * 根因：<b>Mixin 注解处理器不为 {@code @Shadow} 成员生成 refmap 条目</b>（本工程 refmap 里
 * 确实没有它），而 Forge 1.20.1 生产运行时的字段名是 SRG 的 {@code f_104889_}，
 * 于是按字面名 {@code level} 找不到。
 * 开发环境是官方名，所以这个错误**只在实机暴露**。</p>
 *
 * <p>改为 {@link Accessor} 形态（与 {@code ButtonAccessor}、{@code LibraryAccessor} 一致，
 * 已被验证能正常进 refmap）。★ 注意：类级 <b>不能</b>写 {@code remap = false}——
 * 目标是 Minecraft 类，必须参与重映射；写了反而拿不到条目。</p>
 */
@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
    @Accessor("level")
    ClientLevel net_music_can_play_bili$level();
}
