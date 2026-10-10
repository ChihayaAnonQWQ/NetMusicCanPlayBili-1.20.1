package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** 按玩家背包槽位确保快捷栏 MP4 拥有服务端权威 DeviceID。 */
public record MP4EnsureInventoryDeviceIdPacket(int inventorySlot) {

    public static MP4EnsureInventoryDeviceIdPacket decode(FriendlyByteBuf buffer) {
        return new MP4EnsureInventoryDeviceIdPacket(buffer.readInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(inventorySlot);
    }

    public static void handle(MP4EnsureInventoryDeviceIdPacket payload,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "mp4_ensure_inventory_device_id", 12)) {
                return;
            }
            int slot = payload.inventorySlot();
            if (slot < 0 || slot >= Math.min(9, player.getInventory().getContainerSize())) {
                return;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof MP4Item)) {
                return;
            }
            ServerLevel level = (ServerLevel) player.level();
            UUID deviceId = MP4DeviceIdentity.getOrCreateUnique(level, player, stack);
            if (deviceId == null) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — 发送端待接 SimpleChannel：ModernTurntableNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)
            NcpbNetworkDispatch.sendToPlayer(player, new MP4InventoryDeviceIdPacket(slot, deviceId));
            // TODO(1.20.1): 依赖待移植 — 发送端待接 SimpleChannel：ModernTurntableNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)
            NcpbNetworkDispatch.sendToPlayer(player, MP4DeviceStateMirrorPacket.fromEntry(deviceId,
                    MP4DeviceStateStore.getOrCreate(level, deviceId, stack),
                    // TODO(1.20.1): 依赖待移植 — link.AudioLinkIndex
                    com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex.hasHeadphoneLinkedToMp4(deviceId)));
        });
        context.setPacketHandled(true);
    }
}
