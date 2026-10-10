package com.zhongbai233.net_music_can_play_bili.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

import com.zhongbai233.net_music_can_play_bili.init.ModAttributes;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkData;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex;
import com.zhongbai233.net_music_can_play_bili.link.HeadphoneAbility;

import java.util.List;
import java.util.UUID;

/** 隐形耳机，用于把唱片机/MP4 音频私有路由给佩戴者。 */
public class InvisibleHeadphonesItem extends Item implements Equipable {
    public InvisibleHeadphonesItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty()) {
            player.setItemSlot(EquipmentSlot.HEAD, stack.copyWithCount(1));
            stack.shrink(1);
            if (player instanceof ServerPlayer serverPlayer) {
                AudioLinkIndex.updatePlayerHeadphones(serverPlayer);
            }
            player.sendSystemMessage(Component.translatable("message.net_music_can_play_bili.headphones.equipped"));
            return InteractionResultHolder.success(stack);
        }
        player.sendSystemMessage(
                Component.translatable("message.net_music_can_play_bili.headphones.equip_slot_occupied"));
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    /**
     * 1.21.1 用 {@code Item.Properties#attributes(ItemAttributeModifiers)} 声明「戴上即生效」的属性；
     * 1.20.1 没有属性修饰符组件，等价能力由本覆写提供（与原版 {@code ArmorItem} 同款做法，
     * {@code LivingEntity} 会读取它的返回值）。
     * {@link HeadphoneAbility} 正是靠这个修饰符判定玩家是否戴着耳机。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.HEAD) {
            return super.getDefaultAttributeModifiers(slot);
        }
        return ImmutableMultimap.of(ModAttributes.HEADPHONES.get(),
                new AttributeModifier(ModAttributes.HEADPHONES_MODIFIER_ID, "headphones", 1.0D,
                        AttributeModifier.Operation.ADDITION));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents,
            TooltipFlag flag) {
        BlockPos turntable = AudioLinkData.readHeadphoneTurntable(stack);
        if (turntable != null) {
            tooltipComponents.add(Component.translatable("tooltip.net_music_can_play_bili.headphones.turntable",
                    turntable.getX(), turntable.getY(), turntable.getZ()).withStyle(ChatFormatting.GRAY));
        }
        UUID mp4 = AudioLinkData.readHeadphoneMediaDevice(stack);
        if (mp4 != null) {
            String shortId = mp4.toString();
            if (shortId.length() > 8) {
                shortId = shortId.substring(0, 8);
            }
            tooltipComponents.add(Component.translatable("tooltip.net_music_can_play_bili.headphones.media_device",
                    shortId)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
