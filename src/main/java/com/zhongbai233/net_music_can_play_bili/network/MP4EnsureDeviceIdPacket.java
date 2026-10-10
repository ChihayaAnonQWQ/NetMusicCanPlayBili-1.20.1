package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** 在聚焦界面接受操作前，确保手持 MP4 拥有服务端权威设备 ID。 */
public record MP4EnsureDeviceIdPacket(InteractionHand hand) {

    public static MP4EnsureDeviceIdPacket decode(FriendlyByteBuf buffer) {
        // 对应原 HAND_CODEC 的 composite 顺序。
        return new MP4EnsureDeviceIdPacket(
                buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(hand == InteractionHand.OFF_HAND);
    }

    public static void handle(MP4EnsureDeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "mp4_ensure_device_id", 4)) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof MP4Item)) {
                return;
            }
            ServerLevel level = (ServerLevel) player.level();
            UUID deviceId = MP4DeviceIdentity.getOrCreateUnique(level, player, stack);
            if (deviceId == null) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — 发送端待接 SimpleChannel：ModernTurntableNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)
            NcpbNetworkDispatch.sendToPlayer(player, new MP4DeviceIdPacket(payload.hand(), deviceId));
            MP4DeviceStateStore.syncQueueCopy(level, deviceId, stack);
            // TODO(1.20.1): 依赖待移植 — 发送端待接 SimpleChannel：ModernTurntableNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)
            NcpbNetworkDispatch.sendToPlayer(player, MP4DeviceStateMirrorPacket.fromEntry(deviceId,
                    MP4DeviceStateStore.getOrCreate(level, deviceId, stack),
                    // TODO(1.20.1): 依赖待移植 — link.AudioLinkIndex
                    com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex.hasHeadphoneLinkedToMp4(deviceId)));
        });
        context.setPacketHandled(true);
    }
}
