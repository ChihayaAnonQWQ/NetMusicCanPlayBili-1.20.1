package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.link.MediaBindingData.MediaSource;
import com.zhongbai233.net_music_can_play_bili.menu.MediaToolBindingMenu;
import com.zhongbai233.net_music_can_play_bili.server.MediaBindingCleanupService;
import com.zhongbai233.net_music_can_play_bili.server.MediaEquipmentBindingService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 媒体管理工具：确认绑定（或在不选目标时解除装备绑定）。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(MediaToolConfirmBindingPacket, Supplier)} 三件套；
 * 原 {@code StreamCodec.unit(...)} 对应无字段的 decode/空 encode，
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record MediaToolConfirmBindingPacket() {
    public static MediaToolConfirmBindingPacket decode(FriendlyByteBuf buffer) {
        return new MediaToolConfirmBindingPacket();
    }

    public void encode(FriendlyByteBuf buffer) {
        // 无字段包：不写出任何数据（等价于原 StreamCodec.unit）。
    }

    public static void handle(MediaToolConfirmBindingPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            // TODO(1.20.1): 依赖待移植 — MediaToolBindingMenu / MediaBindingCleanupService /
            // MediaEquipmentBindingService / MediaBindingData.MediaSource
            if (player == null || !(player.containerMenu instanceof MediaToolBindingMenu menu)) {
                return;
            }
            ItemStack equipment = menu.getSlot(MediaToolBindingMenu.INPUT_SLOT).getItem();
            if (equipment.isEmpty()) {
                player.sendSystemMessage(Component.translatable(
                        "message.net_music_can_play_bili.media_tool.need_equipment_input").withStyle(ChatFormatting.RED));
                return;
            }
            MediaSource source = menu.targetSource(player);
            if (source == null) {
                var result = MediaBindingCleanupService.clearEquipmentBindings(player, equipment);
                player.sendSystemMessage(Component.translatable(
                        result.total() > 0
                                ? "message.net_music_can_play_bili.media_tool.equipment_unlinked"
                                : "message.net_music_can_play_bili.media_tool.equipment_no_links",
                        result.holographicCount(), result.headphoneCount()).withStyle(ChatFormatting.GOLD));
                if (result.total() > 0) {
                    menu.confirmBinding(player);
                }
                menu.broadcastChanges();
                return;
            }
            var result = MediaEquipmentBindingService.bind(player, equipment, source);
            if (result.bound() && menu.confirmBinding(player)) {
                player.getInventory().setChanged();
                menu.refreshTargetBindingStats(player);
            }
        });
        context.setPacketHandled(true);
    }
}
