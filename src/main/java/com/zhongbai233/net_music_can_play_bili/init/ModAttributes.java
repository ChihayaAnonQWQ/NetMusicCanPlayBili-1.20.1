package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组自定义属性注册。
 *
 * <p>1.20.1 移植说明（相对 1.21.1 / NeoForge 的差异）：
 * <ul>
 *   <li>{@code DeferredHolder<T, T>} 是 NeoForge 的类型；Forge 1.20.1 的
 *       {@code DeferredRegister.register(...)} 返回单类型参数的 {@link RegistryObject}。</li>
 *   <li>1.20.1 的 {@link RangedAttribute} 没有 {@code setSentiment}（连同
 *       {@code Attribute.Sentiment} 都是 1.21+ 才引入的），故此处不再声明「正向」语义。</li>
 *   <li>{@code Attribute#setSyncable(boolean)} 在 1.20.1 是存在的，保留以维持原有的
 *       客户端同步行为。</li>
 * </ul>
 */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE,
            NetMusicCanPlayBili.MODID);

    public static final RegistryObject<Attribute> HEADPHONES = ATTRIBUTES.register("headphones",
            () -> new RangedAttribute("attribute.name.net_music_can_play_bili.headphones", 0.0D, 0.0D, 1024.0D)
                    .setSyncable(true));

    public static final RegistryObject<Attribute> HOLOGRAPHIC_GLASSES = ATTRIBUTES.register(
            "holographic_glasses",
            () -> new RangedAttribute("attribute.name.net_music_can_play_bili.holographic_glasses", 0.0D, 0.0D,
                    1024.0D)
                    .setSyncable(true));

    private ModAttributes() {
    }
}
