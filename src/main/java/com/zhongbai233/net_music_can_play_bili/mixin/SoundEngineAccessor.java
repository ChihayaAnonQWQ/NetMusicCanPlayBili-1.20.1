package com.zhongbai233.net_music_can_play_bili.mixin;

import com.mojang.blaze3d.audio.Library;
import com.zhongbai233.net_music_can_play_bili.bridge.SoundEngineBridge;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link SoundEngine} 的私有状态。
 *
 * <p>1.20.1 移植说明：由 {@code @Shadow} 字段改为 {@link Accessor}——
 * 注解处理器不为 {@code @Shadow} 字段生成 refmap 条目，而生产运行时是 SRG 名
 * （{@code loaded} → {@code f_120219_}、{@code library} → {@code f_120220_}）。
 * 详见 {@link LibraryAccessor} 的说明。</p>
 */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor extends SoundEngineBridge {
    @Accessor("loaded")
    @Override
    boolean net_music_can_play_bili$loaded();

    @Accessor("library")
    @Override
    Library net_music_can_play_bili$library();
}
