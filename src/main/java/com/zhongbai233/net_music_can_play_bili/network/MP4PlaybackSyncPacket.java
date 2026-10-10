package com.zhongbai233.net_music_can_play_bili.network;
// 1.20.1 视频未迁移：MP4ClientMediaSync 暂不引用。
import com.zhongbai233.net_music_can_play_bili.client.MP4ClientMediaSync;
import com.zhongbai233.net_music_can_play_bili.client.sync.ClientMediaSyncPayload;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record MP4PlaybackSyncPacket(UUID ownerId, UUID sourceId, int sourceType, int sourceEntityId,
        double sourceX, double sourceY, double sourceZ, boolean playing, int queueIndex, String playUrl, String rawUrl,
        String songName, int durationSeconds, int volumePerMille, String sessionId, long elapsedMillis,
        boolean headphoneRouted, AreaAudioZone areaAudioZone)
        implements ClientMediaSyncPayload {
    public static final int SOURCE_PLAYER = ClientMediaSyncPayload.SOURCE_PLAYER;
    public static final int SOURCE_ITEM = ClientMediaSyncPayload.SOURCE_ITEM;
    public static final int SOURCE_BLOCK = ClientMediaSyncPayload.SOURCE_BLOCK;
    public static final int SOURCE_CONTAINER_ENTITY = ClientMediaSyncPayload.SOURCE_CONTAINER_ENTITY;

    public static MP4PlaybackSyncPacket decode(FriendlyByteBuf buffer) {
        UUID ownerId = buffer.readUUID();
        UUID sourceId = buffer.readUUID();
        int sourceType = buffer.readVarInt();
        int sourceEntityId = buffer.readVarInt();
        double sourceX = buffer.readDouble();
        double sourceY = buffer.readDouble();
        double sourceZ = buffer.readDouble();
        boolean playing = buffer.readBoolean();
        int queueIndex = buffer.readInt();
        String playUrl = buffer.readUtf();
        String rawUrl = buffer.readUtf();
        String songName = buffer.readUtf();
        int durationSeconds = buffer.readInt();
        int volumePerMille = buffer.readInt();
        String sessionId = buffer.readUtf();
        long elapsedMillis = buffer.readVarLong();
        boolean headphoneRouted = buffer.readBoolean();
        return new MP4PlaybackSyncPacket(ownerId, sourceId, sourceType, sourceEntityId, sourceX, sourceY, sourceZ,
                playing, queueIndex, playUrl, rawUrl, songName, durationSeconds, volumePerMille, sessionId,
                elapsedMillis, headphoneRouted, AreaAudioZoneCodec.decode(buffer));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(ownerId);
        buffer.writeUUID(sourceId);
        buffer.writeVarInt(sourceType);
        buffer.writeVarInt(sourceEntityId);
        buffer.writeDouble(sourceX);
        buffer.writeDouble(sourceY);
        buffer.writeDouble(sourceZ);
        buffer.writeBoolean(playing);
        buffer.writeInt(queueIndex);
        buffer.writeUtf(playUrl);
        buffer.writeUtf(rawUrl);
        buffer.writeUtf(songName);
        buffer.writeInt(durationSeconds);
        buffer.writeInt(volumePerMille);
        buffer.writeUtf(sessionId);
        buffer.writeVarLong(elapsedMillis);
        buffer.writeBoolean(headphoneRouted);
        AreaAudioZoneCodec.encode(buffer, areaAudioZone);
    }

    public MP4PlaybackSyncPacket(UUID ownerId, UUID sourceId, int sourceType, int sourceEntityId,
            double sourceX, double sourceY, double sourceZ, boolean playing, int queueIndex, String playUrl,
            String rawUrl, String songName, int durationSeconds, int volumePerMille, String sessionId,
            long elapsedMillis, boolean headphoneRouted) {
        this(ownerId, sourceId, sourceType, sourceEntityId, sourceX, sourceY, sourceZ, playing, queueIndex,
                playUrl, rawUrl, songName, durationSeconds, volumePerMille, sessionId, elapsedMillis,
                headphoneRouted, AreaAudioZone.unrestricted());
    }

    public MP4PlaybackSyncPacket {
        areaAudioZone = areaAudioZone != null ? areaAudioZone : AreaAudioZone.unrestricted();
    }

    public static MP4PlaybackSyncPacket stop(UUID ownerId, int queueIndex) {
        return stop(ownerId, ownerId, queueIndex);
    }

    public static MP4PlaybackSyncPacket stop(UUID ownerId, UUID sourceId, int queueIndex) {
        UUID normalizedSourceId = sourceId != null ? sourceId : ownerId;
        return new MP4PlaybackSyncPacket(ownerId, normalizedSourceId, SOURCE_PLAYER, -1, 0.0D, 0.0D, 0.0D,
                false, queueIndex, "", "", "", 0, 0, "", 0L, false);
    }

    public static void handle(MP4PlaybackSyncPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 1.20.1 已恢复：MP4ClientMediaSync 已迁入工程。
        context.enqueueWork(() -> MP4ClientMediaSync.handleSync(payload));
        context.setPacketHandled(true);
    }
}
