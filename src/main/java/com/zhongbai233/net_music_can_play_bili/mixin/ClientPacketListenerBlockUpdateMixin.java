package com.zhongbai233.net_music_can_play_bili.mixin;

import com.zhongbai233.net_music_can_play_bili.client.pad.PadMapClientCache;
import com.zhongbai233.net_music_can_play_bili.client.terrain.TerrainPreviewManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates Pad map cells only for server incremental block updates.
 * Full chunk loads are intentionally excluded so revisiting an area keeps its cache.
 *
 * <p><b>1.20.1 移植说明</b>：原来用 {@code @Shadow private ClientLevel level;} 读取目标类的私有字段。
 * <b>注解处理器不为 {@code @Shadow} 成员生成 refmap 条目</b>，而生产运行时的字段名是 SRG 的
 * {@code f_104889_}，实机会报
 * {@code InvalidMixinException: @Shadow field level was not located in the target class}
 * 并**导致客户端启动崩溃**（2026-10-10 14:20 实机日志）。故改为
 * {@link ClientPacketListenerAccessor}（{@code @Accessor} 形态，能正常进 refmap）
 * 加一个 {@code @Unique} 私有读取器。</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerBlockUpdateMixin {

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void net_music_can_play_bili$markSingleBlockDirty(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        if (net_music_can_play_bili$isLoaded(pos.getX(), pos.getZ())) {
            ClientLevel level = net_music_can_play_bili$currentLevel();
            PadMapClientCache.markBlockDirty(level, pos);
            TerrainPreviewManager.markBlockDirty(level, pos);
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void net_music_can_play_bili$markSectionBlocksDirty(ClientboundSectionBlocksUpdatePacket packet,
            CallbackInfo ci) {
        packet.runUpdates((pos, state) -> {
            if (net_music_can_play_bili$isLoaded(pos.getX(), pos.getZ())) {
                ClientLevel level = net_music_can_play_bili$currentLevel();
                PadMapClientCache.markChunkDirty(level, Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
                TerrainPreviewManager.markBlockDirty(level, pos);
            }
        });
    }

    /**
     * 目标类的私有 {@code level} 字段（经 accessor 接口读取，见类注释）。
     *
     * <p><b>方法名不能叫 net_music_can_play_bili$level</b>——那与
     * {@link ClientPacketListenerAccessor} 里 accessor 方法的<b>名字完全相同</b>，
     * 两个方法会被合并进同一个目标类，Mixin 报
     * {@code Method overwrite conflict ... Skipping method} 并跳过其中一个（实机日志实证）。
     * 故这里改名，与 accessor 的方法名区分开。</p>
     */
    @Unique
    private ClientLevel net_music_can_play_bili$currentLevel() {
        return ((ClientPacketListenerAccessor) (Object) this).net_music_can_play_bili$level();
    }

    private boolean net_music_can_play_bili$isLoaded(int blockX, int blockZ) {
        ClientLevel level = net_music_can_play_bili$currentLevel();
        return level != null && level.getChunkSource().hasChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }
}
