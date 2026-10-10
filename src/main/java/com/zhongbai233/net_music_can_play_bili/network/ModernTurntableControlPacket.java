package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 现代留声机控制（与 {@link ModernTurntableStopPacket} 配对的请求方向）。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(ModernTurntableControlPacket, Supplier)} 三件套。
 * 原 {@code StreamCodec.composite} 按参数顺序展开为顺序读写：
 * {@code pos}(BlockPos) → {@code action}(VAR_INT) → {@code targetMillis}(VAR_LONG)；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record ModernTurntableControlPacket(BlockPos pos, Action action, long targetMillis) {
    public static ModernTurntableControlPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        Action action = Action.byId(buffer.readVarInt());
        long targetMillis = buffer.readVarLong();
        return new ModernTurntableControlPacket(pos, action, targetMillis);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos());
        buffer.writeVarInt(action().id());
        buffer.writeVarLong(targetMillis());
    }

    public static void handle(ModernTurntableControlPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — ModernTurntableBlockEntity
            if (!(level.getBlockEntity(payload.pos()) instanceof ModernTurntableBlockEntity turntable)) {
                return;
            }

            switch (payload.action()) {
                case REPLAY -> turntable.replayFromBeginning(player);
                case PAUSE -> turntable.pauseFromControl(level);
                case START -> turntable.resumePlayback(player, payload.targetMillis());
                case SEEK -> turntable.seekTo(level, payload.targetMillis());
                case TOGGLE_REPEAT_ONE -> turntable.toggleRepeatOne();
                case CYCLE_REDSTONE_MODE -> turntable.cycleRedstoneMode(level);
                case CYCLE_EXTRACTION_MODE -> turntable.cycleExtractionMode();
                case SET_VOLUME -> {
                    if (player.mayBuild()) {
                        turntable.setVolumePerMille((int) Math.max(0L, Math.min(1000L, payload.targetMillis())));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    public enum Action {
        REPLAY,
        PAUSE,
        START,
        SEEK,
        TOGGLE_REPEAT_ONE,
        CYCLE_REDSTONE_MODE,
        CYCLE_EXTRACTION_MODE,
        SET_VOLUME;

        public int id() {
            return ordinal();
        }

        public static Action byId(int id) {
            Action[] values = values();
            return id >= 0 && id < values.length ? values[id] : START;
        }
    }
}
