package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.network.message.MusicToClientMessage;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSync;
import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import com.zhongbai233.net_music_can_play_bili.client.audio.ClientMediaPreparer;
import com.zhongbai233.net_music_can_play_bili.client.audio.ModernTurntablePlaybackCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 B站 音频注入由 CC 字幕生成的歌词。
 *
 * <p><b>1.20.1 适配说明（相对 1.21.1）</b>：
 * <ul>
 *   <li><b>注入目标</b>：1.21.1 的分支把处理逻辑放在 payload 记录类
 *       {@code MusicToClientMessage} 的 {@code private static void onHandle(MusicToClientMessage)} 中；
 *       NetMusic 1.5.2-forge+mc1.20.1 同样把处理逻辑放在
 *       {@link MusicToClientMessage} 的 {@code private static void onHandle(MusicToClientMessage)}
 *       （{@code javap -p} 实证），故注入点不变，仅需 {@code remap = false}——
 *       这是前置模组的类，不是 Minecraft 类，不参与 SRG 重映射。</li>
 *   <li><b>字段读取改用 accessor</b>：NetMusic 1.20.1 的 {@link MusicToClientMessage}
 *       把 5 个字段（{@code pos}/{@code url}/{@code rawUrl}/{@code timeSecond}/{@code songName}）
 *       全部设为 {@code private final}，且<b>没有公开访问器</b>
 *       （1.21.1 分支用的 NetMusic 是 record 风格、有访问器，故那边可直接 {@code message.url()}）。
 *       又因 {@code onHandle} 是<b>静态</b>方法、注入器也必须是静态，无法用 {@code @Shadow} 实例字段，
 *       故经由 {@link MusicToClientMessageAccessor} 读取。</li>
 * </ul>
 * 白名单判定与歌词拦截逻辑保持不变。调用方签名已按 1.20.1 分支实测核对：
 * {@code PlaybackSync.parse}/{@code parseMinecartAnchor}/{@code Metadata#hasSession}、
 * {@code ClientMediaPreparer#hasStoredBiliSelection(String,String)}、
 * {@code ModernTurntablePlaybackCoordinator#command(BlockPos,String,String,String,int,Metadata,MinecartAnchor,boolean)}
 * /{@code play(ClientPlaybackCommand)}/{@code playCompatible(ClientPlaybackCommand)} 全部存在且参数一致。
 */
@Mixin(value = MusicToClientMessage.class, remap = false)
public abstract class MusicToClientMessageClientMixin {
    @Inject(method = "onHandle", at = @At("HEAD"), cancellable = true)
    private static void net_music_can_play_bili$injectBiliLyrics(MusicToClientMessage message, CallbackInfo ci) {
        MusicToClientMessageAccessor access = (MusicToClientMessageAccessor) (Object) message;
        String url = access.ncpb$url();
        String rawUrl = access.ncpb$rawUrl();

        PlaybackSync.Metadata sync = PlaybackSync.parse(url);
        boolean modernTurntable = sync.hasSession() || net_music_can_play_bili$isModernTurntable(access.ncpb$pos());
        boolean biliSelection = ClientMediaPreparer.hasStoredBiliSelection(rawUrl, url);

        // 现代化唱片机需要我们的 OpenAL 管线；普通唱片机只在 B站选曲时恢复客户端解析和请求头兼容层。
        if (!modernTurntable && !biliSelection) {
            return;
        }

        if (modernTurntable) {
            PlaybackSync.MinecartAnchor minecartAnchor = PlaybackSync.parseMinecartAnchor(url);
            var command = ModernTurntablePlaybackCoordinator.command(access.ncpb$pos(),
                    rawUrl, url, access.ncpb$songName(), access.ncpb$timeSecond(), sync, minecartAnchor,
                    biliSelection);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> ModernTurntablePlaybackCoordinator.play(command));
            ci.cancel();
            return;
        }

        var command = ModernTurntablePlaybackCoordinator.command(access.ncpb$pos(), rawUrl, url,
                access.ncpb$songName(), access.ncpb$timeSecond(), sync, null, biliSelection);
        Minecraft.getInstance().execute(() -> ModernTurntablePlaybackCoordinator.playCompatible(command));
        ci.cancel();
    }

    @Unique
    private static boolean net_music_can_play_bili$isModernTurntable(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof ModernTurntableBlockEntity;
    }
}
