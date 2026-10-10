package com.zhongbai233.net_music_can_play_bili.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端编辑租约请求结果。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ControlConsoleEditLeaseResultPacket, Supplier)} 三件套。
 */
public record ControlConsoleEditLeaseResultPacket(BlockPos pos, Status status, UUID leaseId) {
    public ControlConsoleEditLeaseResultPacket {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        status = Objects.requireNonNull(status, "status");
        if (status == Status.GRANTED && leaseId == null) {
            throw new IllegalArgumentException("granted lease requires leaseId");
        }
    }

    public static ControlConsoleEditLeaseResultPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Status status = Status.fromId(buf.readVarInt());
        UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
        return new ControlConsoleEditLeaseResultPacket(pos, status, leaseId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeVarInt(status().id);
        buf.writeBoolean(leaseId() != null);
        if (leaseId() != null) {
            buf.writeUUID(leaseId());
        }
    }

    public static void handle(ControlConsoleEditLeaseResultPacket payload,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 1.20.1 最小可用版本：ControlConsoleClient 尚未迁移，暂不应用编辑租约结果。
            // 原实现：com.zhongbai233.net_music_can_play_bili.client.ControlConsoleClient
            //         .acceptLeaseResult(payload);
        });
        context.setPacketHandled(true);
    }

    public enum Status {
        GRANTED(0), BUSY(1), REJECTED(2), EXPIRED(3);

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
            throw new IllegalArgumentException("unknown edit lease result status: " + id);
        }
    }
}
