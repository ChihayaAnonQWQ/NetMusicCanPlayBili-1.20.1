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
 * 可作为耳机使用的装备的共用判定。
 *
 * <p>1.20.1 移植说明（相对 1.21.1 / NeoForge 的差异）：
 * <ul>
 *   <li>{@code EquipmentSlotGroup} 是 1.20.5+ 才引入的（{@code HEAD} 组）；
 *       1.20.1 直接用 {@link EquipmentSlot#HEAD}。</li>
 *   <li>1.21.1 的 {@code ItemStack#forEachModifier(EquipmentSlotGroup, BiConsumer)} 在
 *       1.20.1 对应 {@code ItemStack#getAttributeModifiers(EquipmentSlot)}（返回 Multimap）。</li>
 *   <li><b>附魔属性修饰符</b>：1.21.1 里附魔可以通过 {@code effects} 提供属性修饰符，
 *       故原实现还会查一遍 {@code EnchantmentHelper.forEachModifier}。
 *       <b>1.20.1 的附魔没有属性效果</b>（那是 1.21 的特性，且 1.20.1 的
 *       {@code EnchantmentHelper} 也没有对应方法），所以这里只有物品默认修饰符一条来源。
 *       本模组的耳机/全息眼镜属性由物品类覆写
 *       {@code Item#getDefaultAttributeModifiers(EquipmentSlot)} 提供
 *       （见 {@code InvisibleHeadphonesItem}、{@code HolographicGlassesItem}）。</li>
 *   <li>1.20.1 的 {@code Attribute} 不是 {@code Holder}，没有 {@code is(ResourceKey)}；
 *       属性是注册表单例，直接用引用比较。{@link Registries#ENCHANTMENT} 与
 *       {@link ResourceKey} 保留用于附魔书查询。</li>
 * </ul>
 */
public final class HeadphoneAbility {
    public static final ResourceLocation HEADPHONES_ID = new ResourceLocation(NetMusicCanPlayBili.MODID,
            "headphones");
    public static final ResourceKey<Enchantment> HEADPHONES_KEY = ResourceKey.create(Registries.ENCHANTMENT,
            HEADPHONES_ID);

    private HeadphoneAbility() {
    }

    public static boolean has(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return isHeadEquipment(stack) && hasHeadphoneAttribute(stack);
    }

    private static boolean isHeadEquipment(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        return equipable != null && equipable.getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    private static boolean hasHeadphoneAttribute(ItemStack stack) {
        Attribute headphoneAttribute = ModAttributes.HEADPHONES.get();
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.HEAD);
        for (var entry : modifiers.entries()) {
            // 1.20.1：AttributeModifier 只有 getAmount()，1.21.1 的 record 风格 amount() 不存在。
            if (entry.getKey() == headphoneAttribute && entry.getValue().getAmount() > 0.0D) {
                return true;
            }
        }
        return false;
    }
}
