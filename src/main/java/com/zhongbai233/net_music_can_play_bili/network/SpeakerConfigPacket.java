package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SpeakerConfigPacket(BlockPos pos, int channelIndex, float volume,
        boolean autoMixJoc) {

    public static SpeakerConfigPacket decode(FriendlyByteBuf buffer) {
        // 对应原 composite 顺序：BlockPos → INT → FLOAT → BOOL。
        BlockPos pos = buffer.readBlockPos();
        int channelIndex = buffer.readInt();
        float volume = buffer.readFloat();
        boolean autoMixJoc = buffer.readBoolean();
        return new SpeakerConfigPacket(pos, channelIndex, volume, autoMixJoc);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeInt(channelIndex);
        buffer.writeFloat(volume);
        buffer.writeBoolean(autoMixJoc);
    }

    public static void handle(SpeakerConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — blockentity.SpeakerBlockEntity
            if (!(level.getBlockEntity(payload.pos()) instanceof SpeakerBlockEntity be)) {
                return;
            }
            be.setChannelIndex(payload.channelIndex());
            be.setVolume(payload.volume());
            be.setAutoMixJoc(payload.autoMixJoc());
            be.markDirtyAndSync();
        });
        context.setPacketHandled(true);
    }
}
