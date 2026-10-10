package com.zhongbai233.net_music_can_play_bili.network;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSessionId;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/** Requests a fresh MP4 transport URL while retaining the active logical session. */
public record MP4PlaybackRetryPacket(UUID deviceId, String expectedSessionId, long targetMillis) {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SESSION_ID_LENGTH = 128;

    public static MP4PlaybackRetryPacket decode(FriendlyByteBuf buffer) {
        return new MP4PlaybackRetryPacket(buffer.readUUID(), buffer.readUtf(MAX_SESSION_ID_LENGTH),
                buffer.readVarLong());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(deviceId);
        buffer.writeUtf(expectedSessionId != null ? expectedSessionId : "", MAX_SESSION_ID_LENGTH);
        buffer.writeVarLong(Math.max(0L, targetMillis));
    }

    public static void handle(MP4PlaybackRetryPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || payload == null || payload.deviceId() == null) {
                return;
            }
            PlaybackSessionId expectedSessionId = PlaybackSessionId.parse(payload.expectedSessionId()).orElse(null);
            if (expectedSessionId == null) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "mp4_playback_retry", 4)) {
                LOGGER.debug("丢弃过频 MP4 播放重试包: player={} device={} session={}", player.getUUID(),
                        payload.deviceId(), expectedSessionId);
                return;
            }
            ItemStack stack = MP4Item.findByDeviceId(player, payload.deviceId());
            if (stack == null || !(stack.getItem() instanceof MP4Item)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            UUID authoritativeDeviceId = MP4DeviceIdentity.getOrCreateUnique(level, player, stack);
            if (!payload.deviceId().equals(authoritativeDeviceId)) {
                return;
            }
            MP4PlaybackControlPacket.retryPlayback(player, stack, payload.deviceId(), expectedSessionId,
                    Math.max(0L, payload.targetMillis()));
        });
        context.setPacketHandled(true);
    }
}
