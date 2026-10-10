package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.github.tartaricacid.netmusic.network.message.SetMusicIDMessage;
import com.zhongbai233.net_music_can_play_bili.bili.BiliSongInfoSanitizer;
import com.zhongbai233.net_music_can_play_bili.server.BiliWhitelistManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 服务端兜底：启用白名单后拒绝创建不在白名单内的 BV/第三方链接唱片。
 *
 * <p><b>1.20.1 移植说明（依据 NetMusic 1.5.2-forge+mc1.20.1 的真实签名，javap 实证）</b>：
 * 1.21.1 分支里本模组与 NetMusic 都跑在 NeoForge 的 payload 网络 API 上，
 * 那时 {@code SetMusicIDMessage} 的处理器是
 * {@code handle(SetMusicIDMessage, IPayloadContext)}、{@code song} 有 record 风格访问器
 * {@code song()}。而 1.20.1 Forge 版 NetMusic 是：</p>
 * <ul>
 *   <li>{@code static void handle(SetMusicIDMessage, Supplier<NetworkEvent.Context>)}；</li>
 *   <li>{@code private final ItemMusicCD.SongInfo song}，<b>私有且无访问器</b>
 *       → 经 {@link SetMusicIDMessageAccessor} 读取；</li>
 *   <li>{@code NetworkEvent.Context} 没有 {@code flow()} / {@code player()}：
 *       方向改用 {@code getDirection() == NetworkDirection.PLAY_TO_SERVER}，
 *       玩家改用 {@code getSender()}。</li>
 * </ul>
 *
 * <p><b>线程注意</b>：{@code @Inject} 在 HEAD，运行在 Forge 的网络线程上（与 1.21.1 分支
 * 在 payload 处理器 HEAD 上做判定的时机一致）。判定本身是只读的；拒绝提示走
 * {@code ServerPlayer#sendSystemMessage}。若后续在实机上观察到与该路径相关的线程问题，
 * 应把判定整体挪进 {@code context.enqueueWork(...)}。</p>
 */
@Mixin(value = SetMusicIDMessage.class, remap = false)
public abstract class SetMusicIDMessageMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void net_music_can_play_bili$guardBiliWhitelist(SetMusicIDMessage message,
            Supplier<NetworkEvent.Context> contextSupplier, CallbackInfo ci) {
        if (message == null || contextSupplier == null) {
            return;
        }
        NetworkEvent.Context context = contextSupplier.get();
        if (context == null || context.getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            return;
        }
        ItemMusicCD.SongInfo song = ((SetMusicIDMessageAccessor) (Object) message).ncpb$song();
        String songUrl = song == null ? null : song.songUrl;
        ItemMusicCD.SongInfo normalized = BiliSongInfoSanitizer.sanitize(song);
        if (normalized != song) {
            // 净化后是新实例：丢弃本次载荷、改为派发净化后的载荷。
            // 递归调用的收敛性依赖 sanitize 的幂等性（已净化过的输入必须返回同一实例）。
            SetMusicIDMessage.handle(new SetMusicIDMessage(Objects.requireNonNull(normalized)), contextSupplier);
            ci.cancel();
            return;
        }
        songUrl = normalized == null ? songUrl : normalized.songUrl;
        boolean forbiddenBiliDirectUrl = BiliSongInfoSanitizer.isForbiddenBiliDirectUrl(songUrl);
        if (!forbiddenBiliDirectUrl && (!BiliWhitelistManager.enabled()
                || BiliWhitelistManager.canonicalResource(songUrl).isEmpty())) {
            return;
        }
        ServerPlayer player = context.getSender();
        if (player == null) {
            ci.cancel();
            return;
        }
        if (forbiddenBiliDirectUrl) {
            player.sendSystemMessage(BiliWhitelistManager.denialMessage(player, songUrl, "创建唱片/音源头"));
            ci.cancel();
            return;
        }
        if (BiliWhitelistManager.isAllowed(player.level().getServer(), songUrl)) {
            return;
        }

        player.sendSystemMessage(BiliWhitelistManager.denialMessage(player, songUrl, "创建唱片/音源头"));
        ci.cancel();
    }

}
