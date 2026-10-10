package com.zhongbai233.net_music_can_play_bili.network;

 import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 聚焦手持界面的服务端权威 MP4 设备 ID 响应。 */
public record MP4DeviceIdPacket(InteractionHand hand, UUID deviceId) {

    public static MP4DeviceIdPacket decode(FriendlyByteBuf buffer) {
        // 对应原 HAND_CODEC + UUID_CODEC 的 composite 顺序。
        InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        UUID deviceId = buffer.readUUID();
        return new MP4DeviceIdPacket(hand, deviceId);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(hand == InteractionHand.OFF_HAND);
        buffer.writeUUID(deviceId);
    }

    public static void handle(MP4DeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // TODO(1.20.1): 依赖待移植 — client.MP4Client
         context.enqueueWork(() -> MP4Client.receiveDeviceId(payload.hand(), payload.deviceId()));
        context.setPacketHandled(true);
    }
}
