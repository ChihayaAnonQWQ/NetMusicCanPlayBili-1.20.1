package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleAudioElementKey;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElement;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElementPosition;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.media.ControlConsoleRangeGate;
import com.zhongbai233.net_music_can_play_bili.compat.areacontrol.AreaControlAudioCompat;
import com.zhongbai233.net_music_can_play_bili.server.ControlConsoleConsumerLeaseRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 玩家申请、续期或释放中控台媒体消费资格。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ControlConsoleConsumerLeasePacket, Supplier)} 三件套；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record ControlConsoleConsumerLeasePacket(BlockPos pos, Action action, UUID leaseId, long consumerGeneration) {
    public ControlConsoleConsumerLeasePacket {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        action = Objects.requireNonNull(action, "action");
        if (action == Action.RELEASE && leaseId == null) {
            throw new IllegalArgumentException("release requires leaseId");
        }
    }

    public static ControlConsoleConsumerLeasePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Action action = Action.fromId(buf.readVarInt());
        UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
        return new ControlConsoleConsumerLeasePacket(pos, action, leaseId, buf.readVarLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeVarInt(action().id);
        buf.writeBoolean(leaseId() != null);
        if (leaseId() != null) {
            buf.writeUUID(leaseId());
        }
        buf.writeVarLong(consumerGeneration());
    }

    public static void handle(ControlConsoleConsumerLeasePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — ControlConsoleConsumerLeaseRegistry
            ControlConsoleConsumerLeaseRegistry.Key key = key(level, payload.pos(), player.getUUID());
            if (payload.action() == Action.RELEASE) {
                ControlConsoleConsumerLeaseRegistry.release(key, payload.leaseId());
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "control_console_consumer_lease_global", 64)
                || !NetworkRateLimiter.allow(player.getUUID(),
                    "control_console_consumer_lease:" + payload.pos().asLong(), 4)
                    || !level.hasChunk(Math.floorDiv(payload.pos().getX(), 16),
                        Math.floorDiv(payload.pos().getZ(), 16))
                    // TODO(1.20.1): 依赖待移植 — ControlConsoleBlockEntity
                    || !(level.getBlockEntity(payload.pos()) instanceof ControlConsoleBlockEntity console)) {
                    send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.REJECTED, null,
                        payload.consumerGeneration());
                return;
            }
            long now = System.currentTimeMillis();
            boolean existing = ControlConsoleConsumerLeaseRegistry.hasActive(key, now);
            var document = console.document();
            if (!hasValidLoadedSource(level, document)) {
                if (payload.leaseId() != null) {
                    ControlConsoleConsumerLeaseRegistry.release(key, payload.leaseId());
                }
                    send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.REJECTED, null,
                        payload.consumerGeneration());
                return;
            }
            // TODO(1.20.1): 依赖待移植 — ControlConsoleRangeGate
            var range = ControlConsoleRangeGate.evaluate(existing,
                    player.getX() - (payload.pos().getX() + 0.5D),
                    player.getY() - (payload.pos().getY() + 0.5D),
                    player.getZ() - (payload.pos().getZ() + 0.5D),
                    document.hardRangeX(), document.hardRangeY(), document.hardRangeZ());
            if (!range.active()) {
                if (payload.leaseId() != null) {
                    ControlConsoleConsumerLeaseRegistry.release(key, payload.leaseId());
                }
                    send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.OUTSIDE, null,
                        payload.consumerGeneration());
                return;
            }
            UUID leaseId;
            if (existing) {
                if (payload.leaseId() == null) {
                    // 客户端越界后会忘记 leaseId；同一玩家重入时可安全取回并续期自己的现有租约。
                    leaseId = ControlConsoleConsumerLeaseRegistry.acquireOrRenew(key, now);
                } else if (!ControlConsoleConsumerLeaseRegistry.renew(key, payload.leaseId(), now)) {
                    send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.REJECTED, null,
                        payload.consumerGeneration());
                    return;
                } else {
                    leaseId = payload.leaseId();
                }
            } else {
                leaseId = ControlConsoleConsumerLeaseRegistry.acquireOrRenew(key, now);
            }
            send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.GRANTED, leaseId,
                payload.consumerGeneration(), audioOutputZones(level, payload.pos(), document));
        });
        context.setPacketHandled(true);
    }

    public static ControlConsoleConsumerLeaseRegistry.Key key(ServerLevel level, BlockPos pos, UUID playerId) {
        return new ControlConsoleConsumerLeaseRegistry.Key(level.dimension().location().toString(),
                pos.asLong(), playerId);
    }

    private static boolean hasValidLoadedSource(ServerLevel level, ControlConsoleDocument document) {
        if (!document.hasSourceBinding()
                || !level.dimension().location().toString().equals(document.sourceDimension())) {
            return false;
        }
        BlockPos sourcePos = new BlockPos(document.sourceX(), document.sourceY(), document.sourceZ());
        if (!level.hasChunk(Math.floorDiv(sourcePos.getX(), 16), Math.floorDiv(sourcePos.getZ(), 16))) {
            return false;
        }
        var source = level.getBlockEntity(sourcePos);
        // TODO(1.20.1): 依赖待移植 — ModernTurntableBlockEntity / LiveStreamerBlockEntity
        return switch (document.sourceKind()) {
            case TURNTABLE -> source instanceof ModernTurntableBlockEntity;
            case LIVE_STREAMER -> source instanceof LiveStreamerBlockEntity;
        };
    }

    private static void send(ServerPlayer player, BlockPos pos,
            ControlConsoleConsumerLeaseResultPacket.Status status, UUID leaseId, long consumerGeneration) {
        send(player, pos, status, leaseId, consumerGeneration, List.of());
    }

    private static void send(ServerPlayer player, BlockPos pos,
            ControlConsoleConsumerLeaseResultPacket.Status status, UUID leaseId, long consumerGeneration,
            List<ControlConsoleConsumerLeaseResultPacket.AudioOutputZone> audioOutputZones) {
        NcpbNetworkDispatch.sendToPlayer(player,
                new ControlConsoleConsumerLeaseResultPacket(pos, status, leaseId, consumerGeneration,
                        audioOutputZones));
    }

    private static List<ControlConsoleConsumerLeaseResultPacket.AudioOutputZone> audioOutputZones(
            ServerLevel level, BlockPos consolePos, ControlConsoleDocument document) {
        return document.elements().stream()
                .filter(element -> element != null && element.enabled())
                .filter(element -> element.type() == ControlConsoleElement.Type.AUDIO)
                .map(element -> {
                    // TODO(1.20.1): 依赖待移植 — ControlConsoleElementPosition / ControlConsoleAudioElementKey / AreaControlAudioCompat
                    var world = ControlConsoleElementPosition.worldPosition(
                            consolePos.getX(), consolePos.getY(), consolePos.getZ(), element);
                    BlockPos outputPos = BlockPos.containing(world.x, world.y, world.z);
                    return new ControlConsoleConsumerLeaseResultPacket.AudioOutputZone(
                            ControlConsoleAudioElementKey.of(consolePos, element),
                            AreaControlAudioCompat.zoneAt(level, outputPos));
                })
                .toList();
    }

    public enum Action {
        ACQUIRE_OR_RENEW(0), RELEASE(1);

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
            throw new IllegalArgumentException("unknown consumer lease action: " + id);
        }
    }
}
