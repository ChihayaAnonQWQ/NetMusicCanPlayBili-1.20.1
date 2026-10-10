package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.menu.MediaToolBindingMenu;
import com.zhongbai233.net_music_can_play_bili.server.MediaBindingCleanupService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 媒体管理工具：解除当前目标/装备上的媒体绑定。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(MediaToolClearBindingPacket, Supplier)} 三件套；
 * 原 {@code StreamCodec.unit(...)} 对应无字段的 decode/空 encode，
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record MediaToolClearBindingPacket() {
    public static MediaToolClearBindingPacket decode(FriendlyByteBuf buffer) {
        return new MediaToolClearBindingPacket();
    }

    public void encode(FriendlyByteBuf buffer) {
        // 无字段包：不写出任何数据（等价于原 StreamCodec.unit）。
    }

    public static void handle(MediaToolClearBindingPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            // TODO(1.20.1): 依赖待移植 — MediaToolBindingMenu / MediaBindingCleanupService
            if (player == null || !(player.containerMenu instanceof MediaToolBindingMenu menu)) {
                return;
            }
            var targetSource = menu.targetSource(player);
            if (targetSource != null) {
                var stats = MediaBindingCleanupService.clearTargetBindings(player, targetSource);
                player.sendSystemMessage(Component.translatable(
                        stats.total() > 0
                                ? "message.net_music_can_play_bili.media_tool.target_unlinked"
                                : "message.net_music_can_play_bili.media_tool.target_no_links",
                        stats.headphoneCount(), stats.holographicCount()).withStyle(ChatFormatting.GOLD));
                menu.refreshTargetBindingStats(player);
                return;
            }

            ItemStack equipment = menu.getSlot(MediaToolBindingMenu.INPUT_SLOT).getItem();
            if (equipment.isEmpty()) {
                player.sendSystemMessage(Component.translatable(
                        "message.net_music_can_play_bili.media_tool.need_target_or_equipment")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            var result = MediaBindingCleanupService.clearEquipmentBindings(player, equipment);
            player.sendSystemMessage(Component.translatable(
                    result.total() > 0
                            ? "message.net_music_can_play_bili.media_tool.equipment_unlinked"
                            : "message.net_music_can_play_bili.media_tool.equipment_no_links",
                    result.holographicCount(), result.headphoneCount()).withStyle(ChatFormatting.GOLD));
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
