package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端对消费资格的授权结果。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ControlConsoleConsumerLeaseResultPacket, Supplier)} 三件套。
 */
public record ControlConsoleConsumerLeaseResultPacket(BlockPos pos, Status status, UUID leaseId,
    long consumerGeneration, List<AudioOutputZone> audioOutputZones) {
    private static final int MAX_AUDIO_OUTPUTS = 4096;

    public ControlConsoleConsumerLeaseResultPacket {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        status = Objects.requireNonNull(status, "status");
        if (status == Status.GRANTED && leaseId == null) {
            throw new IllegalArgumentException("granted consumer lease requires leaseId");
        }
        audioOutputZones = List.copyOf(Objects.requireNonNull(audioOutputZones, "audioOutputZones"));
        if (audioOutputZones.size() > MAX_AUDIO_OUTPUTS) {
            throw new IllegalArgumentException("too many console audio output zones");
        }
    }

    public ControlConsoleConsumerLeaseResultPacket(BlockPos pos, Status status, UUID leaseId,
            long consumerGeneration) {
        this(pos, status, leaseId, consumerGeneration, List.of());
    }

    public static ControlConsoleConsumerLeaseResultPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Status status = Status.fromId(buf.readVarInt());
        UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
        long generation = buf.readVarLong();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_AUDIO_OUTPUTS) {
            throw new IllegalArgumentException("invalid console audio output zone count: " + count);
        }
        List<AudioOutputZone> zones = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            zones.add(AudioOutputZone.decode(buf));
        }
        return new ControlConsoleConsumerLeaseResultPacket(pos, status, leaseId, generation, zones);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeVarInt(status().id);
        buf.writeBoolean(leaseId() != null);
        if (leaseId() != null) {
            buf.writeUUID(leaseId());
        }
        buf.writeVarLong(consumerGeneration());
        buf.writeVarInt(audioOutputZones().size());
        audioOutputZones().forEach(zone -> zone.encode(buf));
    }

    public static void handle(ControlConsoleConsumerLeaseResultPacket payload,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 1.20.1 最小可用版本：ControlConsoleRenderer 尚未迁移，暂不应用消费资格结果。
            // 原实现：com.zhongbai233.net_music_can_play_bili.client.renderer.ControlConsoleRenderer
            //         .acceptConsumerLeaseResult(payload);
        });
        context.setPacketHandled(true);
    }

    public enum Status {
        GRANTED(0), OUTSIDE(1), REJECTED(2);

        private final int id;

        Status(int id) {
            this.id = id;
        }

        private static Status fromId(int id) {
            for (Status status : values()) {
                if (status.id == id) {
                    return status;
                }
            }
            throw new IllegalArgumentException("unknown consumer lease result status: " + id);
        }
    }

    public record AudioOutputZone(BlockPos outputKey, AreaAudioZone zone) {
        public AudioOutputZone {
            outputKey = Objects.requireNonNull(outputKey, "outputKey").immutable();
            zone = Objects.requireNonNull(zone, "zone");
        }

        private static AudioOutputZone decode(FriendlyByteBuf buffer) {
            return new AudioOutputZone(buffer.readBlockPos(), AreaAudioZoneCodec.decode(buffer));
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(outputKey);
            AreaAudioZoneCodec.encode(buffer, zone);
        }
    }
}
