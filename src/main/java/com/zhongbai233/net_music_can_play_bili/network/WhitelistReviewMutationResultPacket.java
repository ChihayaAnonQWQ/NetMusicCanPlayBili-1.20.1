package com.zhongbai233.net_music_can_play_bili.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** 服务端对白名单审核操作的明确回执，供界面结束等待、清稿、重试或返回列表。 */
public record WhitelistReviewMutationResultPacket(Mutation mutation, Status status,
        String targetId, String message, long requestId) {
    private static final int MAX_TARGET_LENGTH = 512;
    private static final int MAX_MESSAGE_LENGTH = 256;

    public static WhitelistReviewMutationResultPacket decode(FriendlyByteBuf buffer) {
        return new WhitelistReviewMutationResultPacket(
                Mutation.byId(buffer.readVarInt()),
                Status.byId(buffer.readVarInt()),
                buffer.readUtf(MAX_TARGET_LENGTH),
                buffer.readUtf(MAX_MESSAGE_LENGTH),
                readRequestId(buffer));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(mutation.ordinal());
        buffer.writeVarInt(status.ordinal());
        buffer.writeUtf(bounded(targetId, MAX_TARGET_LENGTH), MAX_TARGET_LENGTH);
        buffer.writeUtf(bounded(message, MAX_MESSAGE_LENGTH), MAX_MESSAGE_LENGTH);
        buffer.writeVarLong(Math.max(0L, requestId));
    }

    public WhitelistReviewMutationResultPacket(Mutation mutation, Status status, String targetId, String message) {
        this(mutation, status, targetId, message, 0L);
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public static void sendTo(ServerPlayer player, Mutation mutation, Status status,
            String targetId, String message, long requestId) {
        if (player != null) {
            // TODO(1.20.1): 依赖待移植 — 发送端待接 SimpleChannel：ModernTurntableNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)
            NcpbNetworkDispatch.sendToPlayer(player,
                    new WhitelistReviewMutationResultPacket(mutation, status, targetId, message, requestId));
        }
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private static long readRequestId(FriendlyByteBuf buffer) {
        long value = buffer.readVarLong();
        if (value < 0L) {
            throw new DecoderException("Invalid whitelist mutation request id: " + value);
        }
        return value;
    }

    public static void handle(WhitelistReviewMutationResultPacket payload,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 1.20.1 最小可用版本：WhitelistReviewScreen 尚未迁移，此处暂不路由结果。
        // 原实现：context.enqueueWork(() ->
        //         com.zhongbai233.net_music_can_play_bili.gui.WhitelistReviewScreen
        //                 .routeMutationResult(payload));
        context.setPacketHandled(true);
    }

    public enum Mutation {
        REMOVE,
        COMMENT,
        REFRESH,
        PREVIEW,
        PREVIEW_SEEK;

        private static Mutation byId(int id) {
            Mutation[] values = values();
            if (id < 0 || id >= values.length) {
                throw new DecoderException("Invalid whitelist mutation id: " + id);
            }
            return values[id];
        }
    }

    public enum Status {
        SUCCESS,
        RATE_LIMITED,
        DENIED,
        MISSING,
        INVALID,
        REQUIRED,
        STALE,
        SAVE_FAILED;

        private static Status byId(int id) {
            Status[] values = values();
            if (id < 0 || id >= values.length) {
                throw new DecoderException("Invalid whitelist mutation result id: " + id);
            }
            return values[id];
        }
    }
}
