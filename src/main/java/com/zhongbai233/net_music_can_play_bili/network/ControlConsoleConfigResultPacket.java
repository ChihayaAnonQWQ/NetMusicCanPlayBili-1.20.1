package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity.ReplaceResult;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleConflictAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 服务端对一次中控台自动保存请求的明确结果。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ControlConsoleConfigResultPacket, Supplier)} 三件套；
 * 原来的 {@code UUID_CODEC} 直接展开为 {@code readUUID}/{@code writeUUID}。
 */
public record ControlConsoleConfigResultPacket(BlockPos pos, UUID operationId, long revision, Status status,
        ControlConsoleDocument authoritativeDocument) {
    public ControlConsoleConfigResultPacket(BlockPos pos, UUID operationId, long revision, Status status) {
        this(pos, operationId, revision, status, null);
    }

    public ControlConsoleConfigResultPacket {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        operationId = Objects.requireNonNull(operationId, "operationId");
        status = Objects.requireNonNull(status, "status");
        if (revision < -1L) {
            throw new IllegalArgumentException("revision must be -1 or non-negative");
        }
        ControlConsoleConflictAuthority.validate(status == Status.CONFLICT, revision, authoritativeDocument);
    }

    public static ControlConsoleConfigResultPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID operationId = buf.readUUID();
        long revision = buf.readLong();
        Status status = Status.fromId(buf.readVarInt());
        ControlConsoleDocument authoritative = buf.readBoolean()
                ? ControlConsoleDocumentPacketCodec.decode(buf) : null;
        return new ControlConsoleConfigResultPacket(pos, operationId, revision, status, authoritative);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos());
        buf.writeUUID(operationId());
        buf.writeLong(revision());
        buf.writeVarInt(status().id());
        buf.writeBoolean(authoritativeDocument() != null);
        if (authoritativeDocument() != null) {
            ControlConsoleDocumentPacketCodec.encode(buf, authoritativeDocument());
        }
    }

    public enum Status {
        APPLIED(0), DUPLICATE(1), CONFLICT(2), REJECTED(3), READ_ONLY(4);

        private final int id;

        Status(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }

        static Status fromId(int id) {
            for (Status status : values()) {
                if (status.id == id) {
                    return status;
                }
            }
            throw new IllegalArgumentException("unknown control console result status: " + id);
        }
    }

    public static void handle(ControlConsoleConfigResultPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // ★ 1.20.1 已恢复调用（旧基线移植时这里是空桩）。
        // 目标就是 gui.HolographicScreenConfigTestScreen —— 与 1.21.1 分支的调用形态一致；
        // 真正的分发逻辑在包私有的 HolographicEditorLifecycleScreen 里，
        // 由该 public 转发方法进入（见其 javadoc 里的可见性说明）。
        context.enqueueWork(() -> com.zhongbai233.net_music_can_play_bili.gui.HolographicScreenConfigTestScreen
                .acceptControlConsoleConfigResult(payload));
        context.setPacketHandled(true);
    }

    public static Status fromReplaceResult(ReplaceResult result) {
        return switch (result) {
            case APPLIED -> Status.APPLIED;
            case DUPLICATE -> Status.DUPLICATE;
            case CONFLICT -> Status.CONFLICT;
            case READ_ONLY -> Status.READ_ONLY;
            case REJECTED -> Status.REJECTED;
        };
    }
}
