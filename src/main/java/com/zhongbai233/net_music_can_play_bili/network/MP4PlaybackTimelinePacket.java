package com.zhongbai233.net_music_can_play_bili.network;
// 1.20.1 视频未迁移：MP4ClientMediaSync 暂不引用。
import com.zhongbai233.net_music_can_play_bili.client.MP4ClientMediaSync;
import com.zhongbai233.net_music_can_play_bili.client.sync.ClientMediaTimelinePayload;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** MP4 播放轻量校时包，不重复携带直链 URL。 */
public record MP4PlaybackTimelinePacket(UUID sourceId, String sessionId, long elapsedMillis, int volumePerMille,
        boolean headphoneRouted, AreaAudioZone areaAudioZone)
        implements ClientMediaTimelinePayload {

    public static MP4PlaybackTimelinePacket decode(FriendlyByteBuf buffer) {
        UUID sourceId = buffer.readUUID();
        String sessionId = buffer.readUtf(128);
        long elapsedMillis = buffer.readVarLong();
        int volumePerMille = buffer.readVarInt();
        boolean headphoneRouted = buffer.readBoolean();
        return new MP4PlaybackTimelinePacket(sourceId, sessionId, elapsedMillis, volumePerMille,
                headphoneRouted, AreaAudioZoneCodec.decode(buffer));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(sourceId);
        buffer.writeUtf(sessionId == null ? "" : sessionId, 128);
        buffer.writeVarLong(Math.max(0L, elapsedMillis));
        buffer.writeVarInt(Math.max(0, Math.min(1000, volumePerMille)));
        buffer.writeBoolean(headphoneRouted);
        AreaAudioZoneCodec.encode(buffer, areaAudioZone);
    }

    public MP4PlaybackTimelinePacket(UUID sourceId, String sessionId, long elapsedMillis, int volumePerMille,
            boolean headphoneRouted) {
        this(sourceId, sessionId, elapsedMillis, volumePerMille, headphoneRouted, AreaAudioZone.unrestricted());
    }

    public MP4PlaybackTimelinePacket {
        areaAudioZone = areaAudioZone != null ? areaAudioZone : AreaAudioZone.unrestricted();
    }

    public static void handle(MP4PlaybackTimelinePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 1.20.1 已恢复：MP4ClientMediaSync 已迁入工程。
        context.enqueueWork(() -> MP4ClientMediaSync.handleTimeline(payload));
        context.setPacketHandled(true);
    }
}
