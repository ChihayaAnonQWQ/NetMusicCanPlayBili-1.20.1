package com.zhongbai233.net_music_can_play_bili.link;

import com.google.common.collect.Multimap;
import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import com.zhongbai233.net_music_can_play_bili.init.ModAttributes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 可作为全息眼镜使用的装备的共用判定。
 *
 * <p>与 {@link HeadphoneAbility} 同构，1.20.1 移植说明见该类；这里只把属性换成
 * {@link ModAttributes#HOLOGRAPHIC_GLASSES}。</p>
 */
public final class HolographicGlassesAbility {
    public static final ResourceLocation HOLOGRAPHIC_GLASSES_ID = new ResourceLocation(NetMusicCanPlayBili.MODID,
            "holographic_glasses");
    public static final ResourceKey<Enchantment> HOLOGRAPHIC_GLASSES_KEY = ResourceKey.create(Registries.ENCHANTMENT,
            HOLOGRAPHIC_GLASSES_ID);

    private HolographicGlassesAbility() {
    }

    public static boolean has(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return isHeadEquipment(stack) && hasHolographicAttribute(stack);
    }

    private static boolean isHeadEquipment(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        return equipable != null && equipable.getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    private static boolean hasHolographicAttribute(ItemStack stack) {
        Attribute glassesAttribute = ModAttributes.HOLOGRAPHIC_GLASSES.get();
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.HEAD);
        for (var entry : modifiers.entries()) {
            // 1.20.1：AttributeModifier 只有 getAmount()，1.21.1 的 record 风格 amount() 不存在。
            if (entry.getKey() == glassesAttribute && entry.getValue().getAmount() > 0.0D) {
                return true;
            }
        }
        return false;
    }
}
