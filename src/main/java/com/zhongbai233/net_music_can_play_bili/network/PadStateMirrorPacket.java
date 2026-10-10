package com.zhongbai233.net_music_can_play_bili.network;

 import com.zhongbai233.net_music_can_play_bili.client.PadClient;
import com.zhongbai233.net_music_can_play_bili.client.PadClient;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadDocument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record PadStateMirrorPacket(UUID deviceId, PadDocument document, long updatedGameTime) {

    public static PadStateMirrorPacket decode(FriendlyByteBuf buffer) {
        return new PadStateMirrorPacket(buffer.readUUID(), PadStatePacket.readDocument(buffer), buffer.readLong());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(deviceId);
        PadStatePacket.writeDocument(buffer, document);
        buffer.writeLong(updatedGameTime);
    }

    public static void handle(PadStateMirrorPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 1.20.1 已恢复：PadClient 已迁入工程。
        context.enqueueWork(() -> PadClient.receiveMirroredDocument(
                payload.deviceId(), payload.document(), payload.updatedGameTime()));
        context.setPacketHandled(true);
    }
}
