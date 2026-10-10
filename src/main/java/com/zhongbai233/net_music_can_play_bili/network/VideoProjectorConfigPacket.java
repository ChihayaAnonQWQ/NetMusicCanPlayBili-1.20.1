package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.VideoProjectorBlockEntity;
import com.zhongbai233.net_music_can_play_bili.media.VideoSurfaceBrightness;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record VideoProjectorConfigPacket(BlockPos pos, float yaw, float pitch, float scale,
        float height, float distanceX, float distanceZ, float brightness, int preferredQuality) {

    public static VideoProjectorConfigPacket decode(FriendlyByteBuf buffer) {
        // 对应原 composite 顺序：BlockPos → 7×FLOAT → INT。
        BlockPos pos = buffer.readBlockPos();
        float yaw = buffer.readFloat();
        float pitch = buffer.readFloat();
        float scale = buffer.readFloat();
        float height = buffer.readFloat();
        float distanceX = buffer.readFloat();
        float distanceZ = buffer.readFloat();
        float brightness = buffer.readFloat();
        int preferredQuality = buffer.readInt();
        return new VideoProjectorConfigPacket(pos, yaw, pitch, scale, height, distanceX, distanceZ, brightness,
                preferredQuality);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeFloat(scale);
        buffer.writeFloat(height);
        buffer.writeFloat(distanceX);
        buffer.writeFloat(distanceZ);
        buffer.writeFloat(brightness);
        buffer.writeInt(preferredQuality);
    }

    public VideoProjectorConfigPacket {
        pos = pos.immutable();
        brightness = VideoSurfaceBrightness.normalize(brightness);
    }

    public static void handle(VideoProjectorConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "video_projector_config", 8)) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — blockentity.VideoProjectorBlockEntity
            if (!(level.getBlockEntity(payload.pos()) instanceof VideoProjectorBlockEntity be)) {
                return;
            }
            be.setProjectionYaw(payload.yaw());
            be.setProjectionPitch(payload.pitch());
            be.setProjectionScale(payload.scale());
            be.setProjectionHeight(payload.height());
            be.setProjectionDistanceX(payload.distanceX());
            be.setProjectionDistanceZ(payload.distanceZ());
            be.setProjectionBrightness(payload.brightness());
            be.setPreferredQuality(payload.preferredQuality());
            be.markDirtyAndSync();
        });
        context.setPacketHandled(true);
    }
}
