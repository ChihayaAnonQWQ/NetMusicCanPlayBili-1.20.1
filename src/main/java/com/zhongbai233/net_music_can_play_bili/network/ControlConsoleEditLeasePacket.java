package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.server.ControlConsoleEditLeaseRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 获取、续期或释放中控台编辑租约。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ControlConsoleEditLeasePacket, Supplier)} 三件套；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record ControlConsoleEditLeasePacket(BlockPos pos, Action action, UUID leaseId) {
    public ControlConsoleEditLeasePacket {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        action = Objects.requireNonNull(action, "action");
        if (action != Action.OPEN && leaseId == null) {
            throw new IllegalArgumentException("leaseId is required for " + action);
        }
    }

    public static ControlConsoleEditLeasePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Action action = Action.fromId(buf.readVarInt());
        UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
        return new ControlConsoleEditLeasePacket(pos, action, leaseId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeVarInt(action().id);
        buf.writeBoolean(leaseId() != null);
        if (leaseId() != null) {
            buf.writeUUID(leaseId());
        }
    }

    public static void handle(ControlConsoleEditLeasePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — ControlConsoleEditLeaseRegistry
            ControlConsoleEditLeaseRegistry.Key key = key(level, payload.pos());
            if (payload.action() == Action.RELEASE) {
                ControlConsoleEditLeaseRegistry.release(key, player.getUUID(), payload.leaseId());
                return;
            }
            // [中控台探针] 定位"右键打不开"用；定位完成后删除（把每个条件拆开，日志里直接看出是哪一条拦下的）。
            boolean rateOk = NetworkRateLimiter.allow(player.getUUID(), "control_console_edit_lease", 4);
            double distSqr = player.position().distanceToSqr(Vec3.atCenterOf(payload.pos()));
            boolean chunkOk = level.hasChunk(Math.floorDiv(payload.pos().getX(), 16),
                    Math.floorDiv(payload.pos().getZ(), 16));
            var be = level.getBlockEntity(payload.pos());
            com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili.LOGGER.info(
                    "[中控台探针] 服务端收到 {} pos={} 限流={} 距离平方={} 区块已加载={} 方块实体={}",
                    payload.action(), payload.pos(), rateOk, distSqr, chunkOk,
                    be == null ? "null" : be.getClass().getName());
            if (!rateOk || distSqr > 64.0D || !chunkOk
                    || !(be instanceof ControlConsoleBlockEntity console)) {
                com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili.LOGGER.info(
                        "[中控台探针] 服务端拒绝（REJECTED）并已回包");
                send(player, payload.pos(), ControlConsoleEditLeaseResultPacket.Status.REJECTED, null);
                return;
            }
            console.claimIfUnowned(player.getUUID());
            if (!console.canEdit(player)) {
                send(player, payload.pos(), ControlConsoleEditLeaseResultPacket.Status.REJECTED, null);
                return;
            }
            long now = System.currentTimeMillis();
            if (payload.action() == Action.OPEN) {
                var result = ControlConsoleEditLeaseRegistry.acquire(key, player.getUUID(), now);
                // [中控台探针] 定位"右键打不开"用；定位完成后删除。
                com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili.LOGGER.info(
                        "[中控台探针] 租约 acquire 结果 status={} leaseId={}", result.status(), result.leaseId());
                send(player, payload.pos(), result.status() == ControlConsoleEditLeaseRegistry.Status.GRANTED
                        ? ControlConsoleEditLeaseResultPacket.Status.GRANTED
                        : ControlConsoleEditLeaseResultPacket.Status.BUSY, result.leaseId());
                return;
            }
            boolean renewed = ControlConsoleEditLeaseRegistry.renew(key, player.getUUID(), payload.leaseId(), now);
            send(player, payload.pos(), renewed ? ControlConsoleEditLeaseResultPacket.Status.GRANTED
                    : ControlConsoleEditLeaseResultPacket.Status.EXPIRED, renewed ? payload.leaseId() : null);
        });
        context.setPacketHandled(true);
    }

    public static ControlConsoleEditLeaseRegistry.Key key(ServerLevel level, BlockPos pos) {
        return new ControlConsoleEditLeaseRegistry.Key(level.dimension().location().toString(), pos.asLong());
    }

    private static void send(ServerPlayer player, BlockPos pos, ControlConsoleEditLeaseResultPacket.Status status,
            UUID leaseId) {
        NcpbNetworkDispatch.sendToPlayer(player, new ControlConsoleEditLeaseResultPacket(pos, status, leaseId));
    }

    public enum Action {
        OPEN(0), RENEW(1), RELEASE(2);

        private final int id;

        Action(int id) {
            this.id = id;
        }

        private static Action fromId(int id) {
            for (Action action : values()) {
                if (action.id == id) {
                    return action;
                }
            }
            throw new IllegalArgumentException("unknown edit lease action: " + id);
        }
    }
}
