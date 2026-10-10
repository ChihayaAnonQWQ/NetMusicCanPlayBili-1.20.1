package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.github.tartaricacid.netmusic.network.message.SetMusicIDMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link SetMusicIDMessage} 私有字段的访问器。
 *
 * <p><b>为什么 1.20.1 需要它</b>：本模组的 1.21.1 分支可以直接写 {@code message.song()}，
 * 因为那个版本的 NetMusic 把 {@code song} 提供成了 record 风格的访问器。
 * 而 <b>NetMusic 1.5.2-forge+mc1.20.1 的 {@link SetMusicIDMessage} 没有访问器</b>
 * —— {@code javap} 实证该字段是 {@code private final ItemMusicCD.SongInfo song}，
 * 类上只暴露构造器与 {@code decode}/{@code encode}/{@code handle} 三个静态方法。</p>
 *
 * <p>又因为注入目标是 <b>static</b> 的 {@code handle}，注入器也必须是 static，
 * 无法用 {@code @Shadow} 实例字段，所以改用本 accessor 接口：
 * 注入器里把 message 实例转型为本接口即可读取字段。
 * 这与本工程旧基线里 {@code MusicToClientMessageAccessor} 的做法一致。</p>
 *
 * <p>{@code remap = false}：目标是前置模组 NetMusic 的类，它<b>不参与</b> Minecraft 的
 * 名称重映射（Forge 生产环境只重映射 MC 的成员名），故按模组类处理。</p>
 */
@Mixin(value = SetMusicIDMessage.class, remap = false)
public interface SetMusicIDMessageAccessor {
    @Accessor("song")
    ItemMusicCD.SongInfo ncpb$song();
}
