package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.LyricProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 歌词投影仪配置下发。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(LyricProjectorConfigPacket, Supplier)} 三件套。
 * 原 {@code StreamCodec.composite} 按参数顺序展开为顺序读写：
 * {@code pos} → {@code yaw}/{@code pitch}/{@code scale}/{@code height}/
 * {@code distanceX}/{@code distanceZ}(FLOAT) → {@code mode}(INT) → {@code allowAi}(BOOL)；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record LyricProjectorConfigPacket(BlockPos pos, float yaw, float pitch, float scale,
        float height, float distanceX, float distanceZ, int mode, boolean allowAi) {
    public static LyricProjectorConfigPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        float yaw = buffer.readFloat();
        float pitch = buffer.readFloat();
        float scale = buffer.readFloat();
        float height = buffer.readFloat();
        float distanceX = buffer.readFloat();
        float distanceZ = buffer.readFloat();
        int mode = buffer.readInt();
        boolean allowAi = buffer.readBoolean();
        return new LyricProjectorConfigPacket(pos, yaw, pitch, scale, height, distanceX, distanceZ, mode, allowAi);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos());
        buffer.writeFloat(yaw());
        buffer.writeFloat(pitch());
        buffer.writeFloat(scale());
        buffer.writeFloat(height());
        buffer.writeFloat(distanceX());
        buffer.writeFloat(distanceZ());
        buffer.writeInt(mode());
        buffer.writeBoolean(allowAi());
    }

    public static void handle(LyricProjectorConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "lyric_projector_config", 8)) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — LyricProjectorBlockEntity
            if (!(level.getBlockEntity(payload.pos()) instanceof LyricProjectorBlockEntity be)) {
                return;
            }
            be.setProjectionYaw(payload.yaw());
            be.setProjectionPitch(payload.pitch());
            be.setProjectionScale(payload.scale());
            be.setProjectionHeight(payload.height());
            be.setProjectionDistanceX(payload.distanceX());
            be.setProjectionDistanceZ(payload.distanceZ());
            be.setProjectionMode(payload.mode());
            be.setAllowAi(payload.allowAi());
            be.markDirtyAndSync();
        });
        context.setPacketHandled(true);
    }
}
