package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElement;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleSnapshotBudget;
import com.zhongbai233.net_music_can_play_bili.server.ControlConsolePermissionPolicy;
import com.zhongbai233.net_music_can_play_bili.server.NetMusicPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 中控台自动保存请求（整份文档）。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ControlConsoleConfigPacket, Supplier)} 三件套；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 * 元素列表原本就是手写的 {@code writeVarInt(size) + 循环}，顺序保持不变。
 */
public record ControlConsoleConfigPacket(BlockPos pos, UUID leaseId, UUID operationId, long expectedRevision,
    String displayName,
    double hardRangeX, double hardRangeY, double hardRangeZ,
    List<ControlConsoleElement> elements) {
    public ControlConsoleConfigPacket {
        pos = pos.immutable();
        leaseId = Objects.requireNonNull(leaseId, "leaseId");
        operationId = Objects.requireNonNull(operationId, "operationId");
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        elements = List.copyOf(elements);
        ControlConsoleSnapshotBudget.requireWithinLimit(displayName, elements);
    }

    public static ControlConsoleConfigPacket decode(FriendlyByteBuf buf) {
        int startIndex = buf.readerIndex();
        BlockPos pos = buf.readBlockPos();
        UUID leaseId = buf.readUUID();
        UUID operationId = buf.readUUID();
        long revision = buf.readLong();
        String name = buf.readUtf(64);
        double rangeX = buf.readDouble();
        double rangeY = buf.readDouble();
        double rangeZ = buf.readDouble();
        int count = buf.readVarInt();
        if (count < 0 || count > ControlConsoleDocument.MAX_ELEMENTS) {
            throw new IllegalArgumentException("invalid control console element count: " + count);
        }
        List<ControlConsoleElement> elements = new ArrayList<>(count);
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            ControlConsoleElement element = readElement(buf);
            if (!ids.add(element.elementId())) throw new IllegalArgumentException("duplicate elementId");
            elements.add(element);
            if (buf.readerIndex() - startIndex > ControlConsoleSnapshotBudget.MAX_BYTES) {
                throw new IllegalArgumentException("control console snapshot exceeds 64 KiB");
            }
        }
        return new ControlConsoleConfigPacket(pos, leaseId, operationId, revision,
                name, rangeX, rangeY, rangeZ,
                List.copyOf(elements));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeUUID(leaseId());
        buf.writeUUID(operationId());
        buf.writeLong(expectedRevision());
        buf.writeUtf(displayName(), 64);
        buf.writeDouble(hardRangeX());
        buf.writeDouble(hardRangeY());
        buf.writeDouble(hardRangeZ());
        List<ControlConsoleElement> elements = List.copyOf(this.elements());
        if (elements.size() > ControlConsoleDocument.MAX_ELEMENTS) {
            throw new IllegalArgumentException("too many control console elements");
        }
        ControlConsoleSnapshotBudget.requireWithinLimit(displayName(), elements);
        buf.writeVarInt(elements.size());
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for (ControlConsoleElement element : elements) {
            if (!ids.add(element.elementId())) throw new IllegalArgumentException("duplicate elementId");
            writeElement(buf, element);
        }
    }

    public static void handle(ControlConsoleConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)
                // TODO(1.20.1): 依赖待移植 — ControlConsolePermissionPolicy / NetMusicPermissions
                || !ControlConsolePermissionPolicy.passesBuildGate(player.mayBuild(),
                    NetMusicPermissions.canAdministerControlConsole(player.createCommandSourceStack()))
                || !NetworkRateLimiter.allow(player.getUUID(), "control_console_config", 4)
                || player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D
                || !level.hasChunk(Math.floorDiv(payload.pos().getX(), 16),
                    Math.floorDiv(payload.pos().getZ(), 16))
                // TODO(1.20.1): 依赖待移植 — ControlConsoleBlockEntity
                || !(level.getBlockEntity(payload.pos()) instanceof ControlConsoleBlockEntity console)) {
                if (player != null) {
                    NcpbNetworkDispatch.sendToPlayer(player, new ControlConsoleConfigResultPacket(payload.pos(),
                        payload.operationId(), -1L, ControlConsoleConfigResultPacket.Status.REJECTED));
                }
                return;
            }
            console.claimIfUnowned(player.getUUID());
            if (!console.canEdit(player)
                // TODO(1.20.1): 依赖待移植 — ControlConsoleEditLeaseRegistry
                || !com.zhongbai233.net_music_can_play_bili.server.ControlConsoleEditLeaseRegistry.validate(
                    ControlConsoleEditLeasePacket.key(level, payload.pos()), player.getUUID(), payload.leaseId(),
                    System.currentTimeMillis())) {
                NcpbNetworkDispatch.sendToPlayer(player, new ControlConsoleConfigResultPacket(payload.pos(),
                        payload.operationId(), console.documentRevision(),
                        ControlConsoleConfigResultPacket.Status.REJECTED));
                return;
            }
            try {
                // TODO(1.20.1): 依赖待移植 — ControlConsoleBlockEntity.ReplaceResult
                ControlConsoleBlockEntity.ReplaceResult result = console.replaceDocument(payload.operationId(),
                    payload.expectedRevision(), payload.displayName(), payload.hardRangeX(), payload.hardRangeY(),
                    payload.hardRangeZ(), payload.elements());
                sendResult(player, payload, console, result);
            } catch (IllegalArgumentException ignored) {
                NcpbNetworkDispatch.sendToPlayer(player, new ControlConsoleConfigResultPacket(payload.pos(),
                    payload.operationId(), console.documentRevision(),
                    ControlConsoleConfigResultPacket.Status.REJECTED));
            }
        });
        context.setPacketHandled(true);
    }

    private static void sendResult(ServerPlayer player, ControlConsoleConfigPacket payload,
            ControlConsoleBlockEntity console, ControlConsoleBlockEntity.ReplaceResult result) {
        ControlConsoleConfigResultPacket.Status status = ControlConsoleConfigResultPacket.fromReplaceResult(result);
        NcpbNetworkDispatch.sendToPlayer(player, new ControlConsoleConfigResultPacket(payload.pos(),
                payload.operationId(), console.documentRevision(), status,
                status == ControlConsoleConfigResultPacket.Status.CONFLICT ? console.document() : null));
    }

    static ControlConsoleElement readElement(FriendlyByteBuf buf) {
        return new ControlConsoleElement(buf.readUUID(), ControlConsoleElement.Type.parse(buf.readUtf(16)),
                buf.readUtf(ControlConsoleElement.MAX_NAME_LENGTH),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(32), buf.readUtf(4096),
            buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readInt(), buf.readFloat(),
            buf.readInt(), buf.readFloat(), buf.readBoolean(), buf.readInt(), buf.readInt(),
            ControlConsoleElement.Alignment.values()[buf.readUnsignedByte()], buf.readFloat(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    static void writeElement(FriendlyByteBuf buf, ControlConsoleElement element) {
        buf.writeUUID(element.elementId());
        buf.writeUtf(element.type().name(), 16);
        buf.writeUtf(element.name(), ControlConsoleElement.MAX_NAME_LENGTH);
        buf.writeFloat(element.distance());
        buf.writeFloat(element.offsetX());
        buf.writeFloat(element.offsetY());
        buf.writeFloat(element.height());
        buf.writeFloat(element.aspect());
        buf.writeFloat(element.yaw());
        buf.writeFloat(element.pitch());
        buf.writeFloat(element.roll());
        buf.writeUtf(element.contentMode(), 32);
        buf.writeUtf(element.text(), 4096);
        buf.writeBoolean(element.followLyrics());
        buf.writeBoolean(element.showTranslation());
        buf.writeFloat(element.textScale());
        buf.writeInt(element.color());
        buf.writeFloat(element.volume());
        buf.writeInt(element.channelIndex());
        buf.writeFloat(element.maxDistance());
        buf.writeBoolean(element.autoMixJoc());
        buf.writeInt(element.translationColor());
        buf.writeInt(element.backgroundColor());
        buf.writeByte(element.alignment().ordinal());
        buf.writeFloat(element.maxWidth());
        buf.writeBoolean(element.wrap());
        buf.writeBoolean(element.enabled());
        buf.writeBoolean(element.locked());
        buf.writeFloat(element.scaleX());
        buf.writeFloat(element.scaleY());
        buf.writeFloat(element.scaleZ());
        buf.writeFloat(element.pivotX());
        buf.writeFloat(element.pivotY());
        buf.writeFloat(element.pivotZ());
        buf.writeFloat(element.skewXByY());
        buf.writeFloat(element.skewYByX());
        buf.writeFloat(element.brightness());
    }
}
