package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.menu.MediaToolReportMenu;
import com.zhongbai233.net_music_can_play_bili.server.PlaybackAuditManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 媒体管理工具：举报当前播放源。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(MediaToolReportPacket, Supplier)} 三件套；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record MediaToolReportPacket(String sourceKey) {
    private static final int MAX_KEY_LENGTH = 256;

    public static MediaToolReportPacket decode(FriendlyByteBuf buffer) {
        return new MediaToolReportPacket(buffer.readUtf(MAX_KEY_LENGTH));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(sourceKey() != null ? sourceKey() : "", MAX_KEY_LENGTH);
    }

    public static void handle(MediaToolReportPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            // TODO(1.20.1): 依赖待移植 — MediaToolReportMenu / PlaybackAuditManager
            if (player == null
                    || !(player.containerMenu instanceof MediaToolReportMenu menu)
                    || !menu.containsSourceKey(payload.sourceKey())) {
                return;
            }
            PlaybackAuditManager.ActiveSource source = PlaybackAuditManager.findByKey(
                    player.level().getServer(), payload.sourceKey());
            if (source == null) {
                player.sendSystemMessage(Component.translatable(
                        "message.net_music_can_play_bili.media_tool.no_active_source").withStyle(ChatFormatting.RED));
                return;
            }
            PlaybackAuditManager.ReportResult result = PlaybackAuditManager.notifyOpsOfReport(player, source,
                    "玩家使用媒体管理工具举报");
            String messageKey = result.notifiedOps()
                    ? "message.net_music_can_play_bili.media_tool.report_sent"
                    : result.merged()
                            ? "message.net_music_can_play_bili.media_tool.report_merged"
                            : "message.net_music_can_play_bili.media_tool.report_logged";
            player.sendSystemMessage(Component.translatable(messageKey, result.totalReports(), result.uniqueReporters())
                    .withStyle(ChatFormatting.GOLD));
            player.closeContainer();
        });
        context.setPacketHandled(true);
    }
}
