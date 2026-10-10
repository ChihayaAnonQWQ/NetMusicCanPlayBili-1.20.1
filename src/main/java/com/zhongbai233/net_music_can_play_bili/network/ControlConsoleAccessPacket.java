package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 权限配置与普通场景快照分离，避免公共编辑者借场景保存提升自身权限。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ControlConsoleAccessPacket, Supplier)} 三件套；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record ControlConsoleAccessPacket(BlockPos pos, UUID leaseId, UUID operationId, long expectedRevision,
        ControlConsoleDocument.AccessMode accessMode, Set<UUID> trustedPlayerIds) {
    public ControlConsoleAccessPacket {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        leaseId = Objects.requireNonNull(leaseId, "leaseId");
        operationId = Objects.requireNonNull(operationId, "operationId");
        accessMode = Objects.requireNonNull(accessMode, "accessMode");
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        trustedPlayerIds = Set.copyOf(Objects.requireNonNull(trustedPlayerIds, "trustedPlayerIds"));
        if (trustedPlayerIds.size() > ControlConsoleDocument.MAX_TRUSTED_PLAYERS) {
            throw new IllegalArgumentException("too many trusted players");
        }
    }

    public static ControlConsoleAccessPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID leaseId = buf.readUUID();
        UUID operationId = buf.readUUID();
        long revision = buf.readLong();
        ControlConsoleDocument.AccessMode mode = ControlConsoleDocument.AccessMode.parse(buf.readUtf(16));
        int count = buf.readVarInt();
        if (count < 0 || count > ControlConsoleDocument.MAX_TRUSTED_PLAYERS) {
            throw new IllegalArgumentException("invalid trusted player count: " + count);
        }
        Set<UUID> trusted = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            trusted.add(buf.readUUID());
        }
        return new ControlConsoleAccessPacket(pos, leaseId, operationId, revision, mode, trusted);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeUUID(leaseId());
        buf.writeUUID(operationId());
        buf.writeLong(expectedRevision());
        buf.writeUtf(accessMode().name(), 16);
        buf.writeVarInt(trustedPlayerIds().size());
        trustedPlayerIds().forEach(buf::writeUUID);
    }

    public static void handle(ControlConsoleAccessPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)
                    || !NetworkRateLimiter.allow(player.getUUID(), "control_console_access", 2)
                    || player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D
                    || !level.hasChunk(Math.floorDiv(payload.pos().getX(), 16),
                        Math.floorDiv(payload.pos().getZ(), 16))
                    // TODO(1.20.1): 依赖待移植 — ControlConsoleBlockEntity
                    || !(level.getBlockEntity(payload.pos()) instanceof ControlConsoleBlockEntity console)) {
                reject(payload, context, -1L);
                return;
            }
            // TODO(1.20.1): 依赖待移植 — ControlConsoleEditLeaseRegistry
            if (!com.zhongbai233.net_music_can_play_bili.server.ControlConsoleEditLeaseRegistry.validate(
                    ControlConsoleEditLeasePacket.key(level, payload.pos()), player.getUUID(), payload.leaseId(),
                    System.currentTimeMillis())) {
                reject(payload, context, console.documentRevision());
                return;
            }
            // TODO(1.20.1): 依赖待移植 — ControlConsoleBlockEntity.ReplaceResult
            ControlConsoleBlockEntity.ReplaceResult result = console.replaceAccessControl(player,
                    payload.operationId(), payload.expectedRevision(), payload.accessMode(), payload.trustedPlayerIds());
            ControlConsoleConfigResultPacket.Status status = ControlConsoleConfigResultPacket.fromReplaceResult(result);
            NcpbNetworkDispatch.sendToPlayer(player, new ControlConsoleConfigResultPacket(payload.pos(),
                    payload.operationId(), console.documentRevision(), status,
                    status == ControlConsoleConfigResultPacket.Status.CONFLICT ? console.document() : null));
        });
        context.setPacketHandled(true);
    }

    private static void reject(ControlConsoleAccessPacket payload, NetworkEvent.Context context, long revision) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            NcpbNetworkDispatch.sendToPlayer(player, new ControlConsoleConfigResultPacket(payload.pos(),
                    payload.operationId(), revision, ControlConsoleConfigResultPacket.Status.REJECTED));
        }
    }
}
