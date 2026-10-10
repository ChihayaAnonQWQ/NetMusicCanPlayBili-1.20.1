package com.zhongbai233.net_music_can_play_bili.mixin;

import com.zhongbai233.net_music_can_play_bili.bridge.SoundManagerBridge;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link SoundManager} 的私有 {@link SoundEngine}。
 *
 * <p>1.20.1 移植说明：由 {@code @Shadow} 字段改为 {@link Accessor}——
 * 注解处理器不为 {@code @Shadow} 字段生成 refmap 条目，而生产运行时是 SRG 名
 * （{@code soundEngine} → {@code f_120349_}）。详见 {@link LibraryAccessor} 的说明。</p>
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor extends SoundManagerBridge {
    @Accessor("soundEngine")
    @Override
    SoundEngine net_music_can_play_bili$soundEngine();
}
