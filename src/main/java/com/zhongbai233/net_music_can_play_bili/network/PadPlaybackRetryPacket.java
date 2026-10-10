package com.zhongbai233.net_music_can_play_bili.network;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.item.PadItem;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSessionId;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/** Requests a new Pad transport URL without replacing the active logical session. */
public record PadPlaybackRetryPacket(UUID deviceId, UUID pointId, String expectedSessionId, long targetMillis) {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SESSION_ID_LENGTH = 128;

    public static PadPlaybackRetryPacket decode(FriendlyByteBuf buffer) {
        return new PadPlaybackRetryPacket(buffer.readUUID(), buffer.readUUID(),
                buffer.readUtf(MAX_SESSION_ID_LENGTH), buffer.readVarLong());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(deviceId);
        buffer.writeUUID(pointId);
        buffer.writeUtf(expectedSessionId != null ? expectedSessionId : "", MAX_SESSION_ID_LENGTH);
        buffer.writeVarLong(Math.max(0L, targetMillis));
    }

    public static void handle(PadPlaybackRetryPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || payload == null || payload.deviceId() == null
                    || payload.pointId() == null) {
                return;
            }
            PlaybackSessionId expectedSessionId = PlaybackSessionId.parse(payload.expectedSessionId()).orElse(null);
            if (expectedSessionId == null
                    || !PadPlaybackSessionIds.matches(expectedSessionId.value(), payload.deviceId(),
                            payload.pointId())) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "pad_playback_retry", 4)) {
                LOGGER.debug("丢弃过频 Pad 播放重试包: player={} device={} point={} session={}", player.getUUID(),
                        payload.deviceId(), payload.pointId(), expectedSessionId);
                return;
            }
            ItemStack stack = PadItem.findByDeviceId(player, payload.deviceId());
            if (!PadItem.isPad(stack) || !payload.deviceId().equals(PadItem.readDeviceId(stack))) {
                return;
            }
            PadPlaybackControlPacket.retry(player, stack, payload.deviceId(), payload.pointId(),
                    expectedSessionId, Math.max(0L, payload.targetMillis()));
        });
        context.setPacketHandled(true);
    }
}
