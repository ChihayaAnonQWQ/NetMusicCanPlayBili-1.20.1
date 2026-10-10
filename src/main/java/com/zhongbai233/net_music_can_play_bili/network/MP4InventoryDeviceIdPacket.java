package com.zhongbai233.net_music_can_play_bili.network;

 import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 将服务端权威 MP4 DeviceID 回写到客户端玩家背包槽位。 */
public record MP4InventoryDeviceIdPacket(int inventorySlot, UUID deviceId) {

    public static MP4InventoryDeviceIdPacket decode(FriendlyByteBuf buffer) {
        return new MP4InventoryDeviceIdPacket(buffer.readInt(), buffer.readUUID());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(inventorySlot);
        buffer.writeUUID(deviceId);
    }

    public static void handle(MP4InventoryDeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // TODO(1.20.1): 依赖待移植 — client.MP4Client
         context.enqueueWork(() -> MP4Client.receiveInventoryDeviceId(payload.inventorySlot(), payload.deviceId()));
        context.setPacketHandled(true);
    }
}
