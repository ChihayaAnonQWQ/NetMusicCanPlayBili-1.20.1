package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 直播机控制：写直播间号、开播、停止、音量。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(LiveStreamerControlPacket, Supplier)} 三件套。
 * 原 {@code StreamCodec.composite} 按参数顺序展开为顺序读写：
 * {@code pos}(BlockPos) → {@code action}(VAR_INT) →
 * {@code roomInput}(stringUtf8(256)) → {@code value}(VAR_INT)；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record LiveStreamerControlPacket(BlockPos pos, Action action, String roomInput, int value) {
    private static final int MAX_ROOM_INPUT_LENGTH = 256;

    public static LiveStreamerControlPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        Action action = Action.byId(buffer.readVarInt());
        String roomInput = buffer.readUtf(MAX_ROOM_INPUT_LENGTH);
        int value = buffer.readVarInt();
        return new LiveStreamerControlPacket(pos, action, roomInput, value);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos());
        buffer.writeVarInt(action().id());
        buffer.writeUtf(roomInput(), MAX_ROOM_INPUT_LENGTH);
        buffer.writeVarInt(value());
    }

    public static void handle(LiveStreamerControlPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — LiveStreamerBlockEntity
            if (!(level.getBlockEntity(payload.pos()) instanceof LiveStreamerBlockEntity streamer)) {
                return;
            }

            switch (payload.action()) {
                case START -> {
                    if (streamer.setRoomId(level, payload.roomInput(), player)) {
                        streamer.startLive(level, player);
                    }
                }
                case STOP -> streamer.stopLive();
                case SET_ROOM -> streamer.setRoomId(level, payload.roomInput(), player);
                case SET_VOLUME -> {
                    if (player.mayBuild()) {
                        streamer.setVolumePerMille(Math.max(0, Math.min(1000, payload.value())));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    public enum Action {
        START,
        STOP,
        SET_ROOM,
        SET_VOLUME;

        public int id() {
            return ordinal();
        }

        public static Action byId(int id) {
            Action[] values = values();
            return id >= 0 && id < values.length ? values[id] : STOP;
        }
    }
}
