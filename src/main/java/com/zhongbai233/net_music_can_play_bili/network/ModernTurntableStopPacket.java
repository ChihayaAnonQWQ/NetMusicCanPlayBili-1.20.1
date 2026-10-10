package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.audio.ModernTurntablePlaybackCoordinator;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSessionId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 服务端权威的「精确会话停止」包，用于现代留声机播放。
 * <p>
 * 1.20.1 移植说明：原始实现基于 26.1 的
 * {@code CustomPacketPayload} + {@code StreamCodec} + {@code IPayloadContext}。
 * 1.20.1 改用 {@code SimpleChannel}，因此：
 * <ul>
 *   <li>去掉 {@code TYPE} 与 {@code STREAM_CODEC}，注册信息改由
 *       {@code ModernTurntableNetwork} 声明（包 ID 与网络方向）。</li>
 *   <li>编解码改为显式的 {@link #decode(FriendlyByteBuf)} 与
 *       {@link #encode(FriendlyByteBuf)}；{@code BlockPos.STREAM_CODEC}
 *       在 1.20.1 对应 {@link FriendlyByteBuf#readBlockPos()} /
 *       {@link FriendlyByteBuf#writeBlockPos(BlockPos)}。</li>
 *   <li>处理入口接收 {@code Supplier<NetworkEvent.Context>}，
 *       在 {@code enqueueWork} 中回到主线程执行并标记已处理。</li>
 * </ul>
 */
public record ModernTurntableStopPacket(BlockPos pos, String sessionId) {
    private static final int MAX_SESSION_ID_LENGTH = 128;

    public ModernTurntableStopPacket {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        sessionId = PlaybackSessionId.of(sessionId).value();
    }

    public static ModernTurntableStopPacket decode(FriendlyByteBuf buffer) {
        return new ModernTurntableStopPacket(buffer.readBlockPos(), buffer.readUtf(MAX_SESSION_ID_LENGTH));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(sessionId, MAX_SESSION_ID_LENGTH);
    }

    public static void handle(ModernTurntableStopPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ModernTurntablePlaybackCoordinator.stop(payload.pos(), payload.sessionId()));
        context.setPacketHandled(true);
    }
}
