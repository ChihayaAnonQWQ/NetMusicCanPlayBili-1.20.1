package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.audio.ClientAreaAudioZoneRegistry;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sent only when the listener crosses an AreaControl boundary.
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(AreaAudioListenerPacket, Supplier)} 三件套；
 * {@code AreaAudioZoneCodec} 已直接面向 {@code FriendlyByteBuf}，字节布局不变。
 */
public record AreaAudioListenerPacket(AreaAudioZone zone) {
    public AreaAudioListenerPacket {
        zone = Objects.requireNonNull(zone, "zone");
    }

    public static AreaAudioListenerPacket decode(FriendlyByteBuf buffer) {
        return new AreaAudioListenerPacket(AreaAudioZoneCodec.decode(buffer));
    }

    public void encode(FriendlyByteBuf buffer) {
        AreaAudioZoneCodec.encode(buffer, zone);
    }

    public static void handle(AreaAudioListenerPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // TODO(1.20.1): 依赖待移植 — client.audio.ClientAreaAudioZoneRegistry
        context.enqueueWork(() -> ClientAreaAudioZoneRegistry.acceptListener(payload.zone()));
        context.setPacketHandled(true);
    }
}
