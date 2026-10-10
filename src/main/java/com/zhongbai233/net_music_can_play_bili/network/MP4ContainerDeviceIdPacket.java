package com.zhongbai233.net_music_can_play_bili.network;

 import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 将服务端修正后的 MP4 DeviceID 回写到客户端当前容器槽位。 */
public record MP4ContainerDeviceIdPacket(int containerSlotIndex, UUID deviceId) {

    public static MP4ContainerDeviceIdPacket decode(FriendlyByteBuf buffer) {
        return new MP4ContainerDeviceIdPacket(buffer.readInt(), buffer.readUUID());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(containerSlotIndex);
        buffer.writeUUID(deviceId);
    }

    public static void handle(MP4ContainerDeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 1.20.1 已恢复：MP4Client 已迁入工程。
        context.enqueueWork(() -> MP4Client.receiveContainerDeviceId(
                payload.containerSlotIndex(), payload.deviceId()));
        context.setPacketHandled(true);
    }
}
