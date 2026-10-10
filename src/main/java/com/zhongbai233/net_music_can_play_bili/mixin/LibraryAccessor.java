package com.zhongbai233.net_music_can_play_bili.mixin;

import com.mojang.blaze3d.audio.Library;
import com.zhongbai233.net_music_can_play_bili.bridge.LibraryBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link Library} 的私有原生句柄。
 *
 * <p><b>1.20.1 移植说明（为什么不用 {@code @Shadow} 字段）</b>：
 * 本工程原来用「{@code @Shadow} 私有字段 + 桥接接口实现方法」的写法。但
 * <b>Mixin 注解处理器不会为 {@code @Shadow} 字段生成 refmap 条目</b>
 * （实证：本工程产出的 {@code mixin.refmap.json} 里，{@code ButtonAccessor} 的
 * {@code @Accessor("onPress")} 有条目，而所有 {@code @Shadow} 字段——包括
 * {@code ClientPacketListenerBlockUpdateMixin.level}——一个都没有）。
 * 而 <b>Forge 1.20.1 的生产运行时用 SRG 名</b>
 * （{@code context} → {@code f_83687_}、{@code currentDevice} → {@code f_193465_}），
 * 没有 refmap 条目就只能按字面名查找 → 实机 {@code InvalidAccessorException}/找不到字段而崩溃
 * （开发环境是官方名，完全看不出来）。
 * 故统一改为 {@link Accessor} 形态——它与 {@code ButtonAccessor} 一样能被处理器正常映射。</p>
 */
@Mixin(Library.class)
public interface LibraryAccessor extends LibraryBridge {
    @Accessor("context")
    @Override
    long net_music_can_play_bili$context();

    @Accessor("currentDevice")
    @Override
    long net_music_can_play_bili$currentDevice();
}
