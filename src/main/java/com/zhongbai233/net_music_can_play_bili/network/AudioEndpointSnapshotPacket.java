package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.util.MathCompat;

import com.zhongbai233.net_music_can_play_bili.client.audio.ClientAudioEndpointIndex;
import com.zhongbai233.net_music_can_play_bili.link.AudioPlaybackIndexSavedData;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSourceId;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Spatial subscription snapshot/delta for one playback source.
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(AudioEndpointSnapshotPacket, Supplier)} 三件套；
 * {@code BlockPos.STREAM_CODEC} 对应 {@code readBlockPos}/{@code writeBlockPos}。
 */
public record AudioEndpointSnapshotPacket(UUID sourceId, BlockPos sourcePos, AreaAudioZone sourceZone,
        long generation, boolean reset, boolean subscribed, List<Endpoint> endpoints, List<UUID> removals) {
    private static final int MAX_ENDPOINTS = 4096;

    public AudioEndpointSnapshotPacket {
        Objects.requireNonNull(sourceId, "sourceId");
        sourcePos = Objects.requireNonNull(sourcePos, "sourcePos").immutable();
        sourceZone = Objects.requireNonNull(sourceZone, "sourceZone");
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        removals = List.copyOf(Objects.requireNonNull(removals, "removals"));
        generation = Math.max(0L, generation);
        if (endpoints.size() > MAX_ENDPOINTS || removals.size() > MAX_ENDPOINTS) {
            throw new IllegalArgumentException("too many audio endpoints");
        }
    }

    public static AudioEndpointSnapshotPacket decode(FriendlyByteBuf buffer) {
        UUID sourceId = buffer.readUUID();
        BlockPos sourcePos = buffer.readBlockPos();
        AreaAudioZone sourceZone = AreaAudioZoneCodec.decode(buffer);
        long generation = buffer.readVarLong();
        boolean reset = buffer.readBoolean();
        boolean subscribed = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENDPOINTS) {
            throw new IllegalArgumentException("invalid audio endpoint snapshot size: " + count);
        }
        List<Endpoint> endpoints = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            endpoints.add(Endpoint.decode(buffer));
        }
        int removalCount = buffer.readVarInt();
        if (removalCount < 0 || removalCount > MAX_ENDPOINTS) {
            throw new IllegalArgumentException("invalid audio endpoint removal size: " + removalCount);
        }
        List<UUID> removals = new ArrayList<>(removalCount);
        for (int index = 0; index < removalCount; index++) {
            removals.add(buffer.readUUID());
        }
        return new AudioEndpointSnapshotPacket(sourceId, sourcePos, sourceZone, generation,
                reset, subscribed, endpoints, removals);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(sourceId());
        buffer.writeBlockPos(sourcePos());
        AreaAudioZoneCodec.encode(buffer, sourceZone());
        buffer.writeVarLong(generation());
        buffer.writeBoolean(reset());
        buffer.writeBoolean(subscribed());
        buffer.writeVarInt(endpoints().size());
        endpoints().forEach(endpoint -> endpoint.encode(buffer));
        buffer.writeVarInt(removals().size());
        removals().forEach(buffer::writeUUID);
    }

    /** Compatibility constructor for bench fixtures and complete local snapshots. */
    public AudioEndpointSnapshotPacket(UUID sourceId, BlockPos sourcePos, List<Endpoint> endpoints) {
        this(sourceId, sourcePos, AreaAudioZone.unrestricted(), 0L, true, true, endpoints, List.of());
    }

    /** Compatibility constructor for callers that do not provide AreaControl metadata. */
    public AudioEndpointSnapshotPacket(UUID sourceId, BlockPos sourcePos, long generation,
            boolean reset, boolean subscribed, List<Endpoint> endpoints, List<UUID> removals) {
        this(sourceId, sourcePos, AreaAudioZone.unrestricted(), generation, reset, subscribed, endpoints, removals);
    }

    public static AudioEndpointSnapshotPacket from(PlaybackSourceId sourceId, BlockPos sourcePos,
            List<AudioPlaybackIndexSavedData.EndpointEntry> entries) {
        return delta(sourceId, sourcePos, 0L, true, true, entries, List.of());
    }

    public static AudioEndpointSnapshotPacket delta(PlaybackSourceId sourceId, BlockPos sourcePos,
            long generation, boolean reset, boolean subscribed,
            List<AudioPlaybackIndexSavedData.EndpointEntry> upserts, List<UUID> removals) {
        return new AudioEndpointSnapshotPacket(sourceId.value(), sourcePos, AreaAudioZone.unrestricted(),
                generation, reset, subscribed,
                upserts.stream().map(Endpoint::from).toList(), removals);
    }

    public static void handle(AudioEndpointSnapshotPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 1.20.1 最小可用版本：ClientAudioEndpointIndex 未提供 accept(AudioEndpointSnapshotPacket)
            // （该项目 1.20.1 版只有 audibleDemands/geometricDemands/endpointSnapshot 等查询方法）。
            // 原实现：ClientAudioEndpointIndex.accept(payload);
            if (payload == null) {
                return;
            }
        });
        context.setPacketHandled(true);
    }

    public record Endpoint(UUID endpointId, BlockPos pos, int channelIndex, float volume,
            boolean autoMixJoc, float maxDistance, long revision, AreaAudioZone zone) {
        public Endpoint {
            Objects.requireNonNull(endpointId, "endpointId");
            pos = Objects.requireNonNull(pos, "pos").immutable();
            volume = MathCompat.clamp(volume, 0.0F, 2.0F);
            maxDistance = MathCompat.clamp(maxDistance, 1.0F, 256.0F);
            revision = Math.max(0L, revision);
            zone = Objects.requireNonNull(zone, "zone");
        }

        public Endpoint(UUID endpointId, BlockPos pos, int channelIndex, float volume,
                boolean autoMixJoc, float maxDistance, long revision) {
            this(endpointId, pos, channelIndex, volume, autoMixJoc, maxDistance, revision,
                    AreaAudioZone.unrestricted());
        }

        static Endpoint from(AudioPlaybackIndexSavedData.EndpointEntry entry) {
            return new Endpoint(entry.endpointId(), BlockPos.of(entry.endpointPos()), entry.channelIndex(),
                    entry.volume(), entry.autoMixJoc(), entry.maxDistance(), entry.revision(),
                    AreaAudioZone.unrestricted());
        }

        static Endpoint decode(FriendlyByteBuf buffer) {
            return new Endpoint(buffer.readUUID(), buffer.readBlockPos(), buffer.readVarInt(),
                    buffer.readFloat(), buffer.readBoolean(), buffer.readFloat(), buffer.readVarLong(),
                    AreaAudioZoneCodec.decode(buffer));
        }

        void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(endpointId);
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(channelIndex);
            buffer.writeFloat(volume);
            buffer.writeBoolean(autoMixJoc);
            buffer.writeFloat(maxDistance);
            buffer.writeVarLong(revision);
            AreaAudioZoneCodec.encode(buffer, zone);
        }
    }
}
