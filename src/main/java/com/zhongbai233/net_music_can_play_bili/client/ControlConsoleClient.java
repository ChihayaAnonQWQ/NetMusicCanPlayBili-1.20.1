package com.zhongbai233.net_music_can_play_bili.client;

import com.zhongbai233.net_music_can_play_bili.gui.HolographicScreenConfigTestScreen;
import com.zhongbai233.net_music_can_play_bili.gui.ControlConsoleGuideScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.zhongbai233.net_music_can_play_bili.network.NcpbNetworkDispatch;

import java.util.UUID;

/** 控制台客户端编辑入口。具体提交仍由服务端文档协议负责。 */
public final class ControlConsoleClient {
    private static final long RENEW_INTERVAL_MILLIS = 5_000L;
    private static BlockPos pendingOpen;
    private static ActiveLease activeLease;

    private ControlConsoleClient() {
    }

    public static void openScreen(BlockPos pos) {
        // [中控台探针] 定位"右键打不开"用；定位完成后删除。
        com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili.LOGGER.info(
                "[中控台探针] openScreen 进入 pos={} level={} player={} pendingOpen={} activeLease={}",
                pos, Minecraft.getInstance().level != null, Minecraft.getInstance().player != null, pendingOpen,
                activeLease);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            BlockPos immutablePos = pos.immutable();
            if (activeLease != null && activeLease.pos().equals(immutablePos)) {
                openGrantedWorkflow(immutablePos, minecraft);
                return;
            }
            if (activeLease != null) {
                releaseLease(activeLease.pos());
            }
            pendingOpen = immutablePos;
            // [中控台探针] 定位"右键打不开"用；定位完成后删除。
            com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili.LOGGER.info(
                    "[中控台探针] 客户端发送 OPEN 租约请求 pos={}", immutablePos);
            NcpbNetworkDispatch.sendToServer(new com.zhongbai233.net_music_can_play_bili.network
                    .ControlConsoleEditLeasePacket(pos,
                    com.zhongbai233.net_music_can_play_bili.network.ControlConsoleEditLeasePacket.Action.OPEN,
                    null));
        }
    }

    public static void acceptLeaseResult(
            com.zhongbai233.net_music_can_play_bili.network.ControlConsoleEditLeaseResultPacket result) {
        // [中控台探针] 定位"右键打不开"用；定位完成后删除。
        com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili.LOGGER.info(
                "[中控台探针] 收到租约结果 pos={} status={} leaseId={} pendingOpen={} activeLease={}",
                result.pos(), result.status(), result.leaseId(), pendingOpen, activeLease);
        Minecraft minecraft = Minecraft.getInstance();
        if (activeLease != null && activeLease.pos().equals(result.pos())) {
            if (result.status() == com.zhongbai233.net_music_can_play_bili.network
                    .ControlConsoleEditLeaseResultPacket.Status.GRANTED
                    && activeLease.leaseId().equals(result.leaseId())) {
                activeLease = new ActiveLease(activeLease.pos(), activeLease.leaseId(),
                        System.currentTimeMillis() + RENEW_INTERVAL_MILLIS);
                return;
            }
                if (result.status() != com.zhongbai233.net_music_can_play_bili.network
                    .ControlConsoleEditLeaseResultPacket.Status.GRANTED) {
                activeLease = null;
                if (minecraft.screen instanceof HolographicScreenConfigTestScreen
                        || minecraft.screen instanceof ControlConsoleGuideScreen) {
                    minecraft.setScreen(null);
                }
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal("中控台编辑租约已失效"));
                }
                return;
            }
        }
        if (pendingOpen == null || !pendingOpen.equals(result.pos())) {
            return;
        }
        pendingOpen = null;
        if (result.status() != com.zhongbai233.net_music_can_play_bili.network
                .ControlConsoleEditLeaseResultPacket.Status.GRANTED) {
            if (minecraft.player != null) {
                String message = result.status() == com.zhongbai233.net_music_can_play_bili.network
                        .ControlConsoleEditLeaseResultPacket.Status.BUSY
                        ? "该中控台正由其他玩家编辑" : "服务器拒绝打开中控台编辑器";
                minecraft.player.sendSystemMessage(Component.literal(message));
            }
            return;
        }
        activeLease = new ActiveLease(result.pos(), result.leaseId(),
                System.currentTimeMillis() + RENEW_INTERVAL_MILLIS);
        openGrantedWorkflow(result.pos(), minecraft);
    }

    private static void openGrantedWorkflow(BlockPos pos, Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean guideDismissed = ClientPlayerPreferences.defaults()
                .isControlConsoleGuideDismissed(minecraft.player.getUUID());
        // [中控台探针] 定位"右键打不开"用；定位完成后删除。
        com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili.LOGGER.info(
                "[中控台探针] 打开界面 pos={} 引导页已忽略={} → {}", pos, guideDismissed,
                guideDismissed ? "编辑器 HolographicScreenConfigTestScreen" : "引导页 ControlConsoleGuideScreen");
        if (guideDismissed) {
            openEditor(pos);
        } else {
            minecraft.setScreen(new ControlConsoleGuideScreen(pos));
        }
    }

    public static UUID leaseId(BlockPos pos) {
        return activeLease != null && activeLease.pos().equals(pos) ? activeLease.leaseId() : null;
    }

    public static boolean hasLease(BlockPos pos) {
        return leaseId(pos) != null;
    }

    public static void tickLease(BlockPos pos) {
        if (activeLease == null || !activeLease.pos().equals(pos)
                || System.currentTimeMillis() < activeLease.nextRenewMillis()) {
            return;
        }
        NcpbNetworkDispatch.sendToServer(new com.zhongbai233.net_music_can_play_bili.network
                .ControlConsoleEditLeasePacket(pos,
                com.zhongbai233.net_music_can_play_bili.network.ControlConsoleEditLeasePacket.Action.RENEW,
                activeLease.leaseId()));
        activeLease = new ActiveLease(activeLease.pos(), activeLease.leaseId(),
                System.currentTimeMillis() + RENEW_INTERVAL_MILLIS);
    }

    public static void releaseLease(BlockPos pos) {
        if (activeLease == null || !activeLease.pos().equals(pos)) {
            return;
        }
        NcpbNetworkDispatch.sendToServer(new com.zhongbai233.net_music_can_play_bili.network
                .ControlConsoleEditLeasePacket(pos,
                com.zhongbai233.net_music_can_play_bili.network.ControlConsoleEditLeasePacket.Action.RELEASE,
                activeLease.leaseId()));
        activeLease = null;
    }

    public static void clearLease() {
        pendingOpen = null;
        activeLease = null;
    }

    public static void openEditor(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && hasLease(pos)) {
            minecraft.setScreen(HolographicScreenConfigTestScreen.forControlConsole(pos));
        }
    }

    private record ActiveLease(BlockPos pos, UUID leaseId, long nextRenewMillis) {
    }
}