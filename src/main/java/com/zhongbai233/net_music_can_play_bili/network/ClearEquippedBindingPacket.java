package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.link.EquippedMediaItems;
import com.zhongbai233.net_music_can_play_bili.link.HolographicGlassesAbility;
import com.zhongbai233.net_music_can_play_bili.link.HeadphoneAbility;
import com.zhongbai233.net_music_can_play_bili.server.MediaBindingCleanupService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 清除当前装备上的媒体绑定。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ClearEquippedBindingPacket, Supplier)} 三件套；
 * 原 {@code StreamCodec.unit(...)} 对应无字段的 decode/空 encode。
 */
public record ClearEquippedBindingPacket() {
    public static ClearEquippedBindingPacket decode(FriendlyByteBuf buffer) {
        return new ClearEquippedBindingPacket();
    }

    public void encode(FriendlyByteBuf buffer) {
        // 无字段包：不写出任何数据（等价于原 StreamCodec.unit）。
    }

    public static void handle(ClearEquippedBindingPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            boolean[] equipped = { false, false };
            // TODO(1.20.1): 依赖待移植 — EquippedMediaItems / HolographicGlassesAbility / HeadphoneAbility
            EquippedMediaItems.forEachEquipped(player, stack -> {
                equipped[0] |= HolographicGlassesAbility.has(stack);
                equipped[1] |= HeadphoneAbility.has(stack);
            });
            boolean holographic = equipped[0];
            boolean headphones = equipped[1];
            if (!holographic && !headphones) {
                player.sendSystemMessage(Component.translatable(
                        "message.net_music_can_play_bili.equipment_bindings.need_equipped"));
                return;
            }

            // TODO(1.20.1): 依赖待移植 — MediaBindingCleanupService
            var result = MediaBindingCleanupService.clearEquippedHeadBindings(player);
            int glassesCount = result.holographicCount();
            int headphoneCount = result.headphoneCount();
            if (holographic && headphones) {
                player.sendSystemMessage(glassesCount > 0 || headphoneCount > 0
                        ? Component.translatable("message.net_music_can_play_bili.equipment_bindings.cleared_both",
                                glassesCount, headphoneCount)
                        : Component.translatable("message.net_music_can_play_bili.equipment_bindings.none_both"));
            } else if (holographic) {
                player.sendSystemMessage(glassesCount > 0
                        ? Component.translatable("message.net_music_can_play_bili.equipment_bindings.cleared_glasses",
                                glassesCount)
                        : Component.translatable("message.net_music_can_play_bili.equipment_bindings.none_glasses"));
            } else {
                player.sendSystemMessage(headphoneCount > 0
                        ? Component.translatable("message.net_music_can_play_bili.equipment_bindings.cleared_headphones",
                                headphoneCount)
                        : Component.translatable("message.net_music_can_play_bili.equipment_bindings.none_headphones"));
            }
        });
        context.setPacketHandled(true);
    }
}
