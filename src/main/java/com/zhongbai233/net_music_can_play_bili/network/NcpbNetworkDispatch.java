package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.network.NcpbNetworkDispatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

/**
 * 26.1 便捷发包方法在 1.20.1 的等价封装。
 * <p>
 * 1.20.1 移植说明：原始实现调用 NeoForge 的静态便捷方法
 * {@code NcpbNetworkDispatch.sendToPlayer(ServerPlayer, Object)} /
 * {@code sendToPlayersNear(...)} /
 * {@code sendToPlayersTrackingEntityAndSelf(...)} 以及客户端侧的
 * {@code NcpbNetworkDispatch.sendToServer(Object)}。
 * 这些类在 Forge 1.20.1 中**不存在**：1.20.1 的 {@link PacketDistributor}
 * 只提供 {@code PLAYER}/{@code NEAR}/{@code TRACKING_ENTITY_AND_SELF} 等
 * {@link PacketDistributor.PacketTarget} 工厂，且 {@code PacketTarget#send} 只接受原版
 * {@code Packet<?>}，自定义包必须经 {@link ModernTurntableNetwork#CHANNEL} 发送。
 * <p>
 * 本类提供与原始静态方法**同名的等价入口**，使调用点只需把
 * {@code NcpbNetworkDispatch.sendToPlayer(...)} 换成
 * {@code NcpbNetworkDispatch.sendToPlayer(...)}，无需重写调用结构。
 */
public final class NcpbNetworkDispatch {
    private NcpbNetworkDispatch() {
    }

    /** 发送给单个玩家。 */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        ModernTurntableNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** 发送给指定坐标半径内的所有玩家（{@code excluded} 可为 null，表示不排除任何人）。 */
    public static void sendToPlayersNear(ServerLevel level, ServerPlayer excluded,
            double x, double y, double z, double radius, Object message) {
        ModernTurntableNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        excluded, x, y, z, radius, level.dimension())),
                message);
    }

    /** 发送给正在追踪该实体（含其自身客户端）的玩家。 */
    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, Object message) {
        ModernTurntableNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
    }

    /** 发送给正在追踪该实体的玩家（不含其自身）。 */
    public static void sendToPlayersTrackingEntity(Entity entity, Object message) {
        ModernTurntableNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }

    /** 客户端 → 服务端。 */
    public static void sendToServer(Object message) {
        ModernTurntableNetwork.CHANNEL.sendToServer(message);
    }
}
