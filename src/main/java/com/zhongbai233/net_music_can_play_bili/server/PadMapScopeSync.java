package com.zhongbai233.net_music_can_play_bili.server;

import com.zhongbai233.net_music_can_play_bili.network.NcpbNetworkDispatch;
import com.zhongbai233.net_music_can_play_bili.network.PadMapWorldScopePacket;
import com.zhongbai233.net_music_can_play_bili.network.PadMapScopeSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** 登录时同步 Pad 地图缓存 UUID 作用域，避免不同存档/不同后端共用客户端缓存。 */
public final class PadMapScopeSync {
    private PadMapScopeSync() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String worldScopeId = PadMapScopeSavedData.get(server.overworld()).worldScopeId();
        String worldName = server.getWorldData().getLevelName();
        // 1.20.1：NeoForge 的 PacketDistributor.sendToPlayer 静态便捷方法在 Forge 1.20.1 不存在，
        // 改用本工程既有封装（SimpleChannel + PacketDistributor.PLAYER 目标）。
        NcpbNetworkDispatch.sendToPlayer(player, new PadMapWorldScopePacket(worldScopeId, worldName));
    }
}
