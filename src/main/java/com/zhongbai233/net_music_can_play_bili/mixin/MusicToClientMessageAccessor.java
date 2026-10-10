package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.network.message.MusicToClientMessage;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link MusicToClientMessage} 私有字段的访问器。
 *
 * <p><b>为什么 1.20.1 需要它</b>：本模组的 1.21.1 分支可以直接写
 * {@code message.url()} / {@code message.rawUrl()} / {@code message.pos()} 等，
 * 因为那个版本的 NetMusic 把这些字段提供成了 record 风格的访问器。
 * <b>NetMusic 1.20.1 的 {@link MusicToClientMessage} 没有这些访问器</b>
 * ——{@code javap} 实证其 5 个字段（{@code pos}/{@code url}/{@code rawUrl}/
 * {@code timeSecond}/{@code songName}）全部是 {@code private final}，且该类只暴露
 * 构造器与 {@code decode}/{@code encode}/{@code handle} 三个静态方法。</p>
 *
 * <p>又因为 {@code onHandle} 是 <b>static</b> 方法，注入它的方法也必须是 static，
 * 无法用 {@code @Shadow} 实例字段——所以改用本 accessor 接口：
 * 注入器里把 message 实例转型为本接口即可读取字段。</p>
 */
// remap = false：目标是前置模组 NetMusic 的类，它不参与 Minecraft 的名称重映射
// （生产环境只把 MC 的成员名变成 SRG），字段名 pos/url/rawUrl/timeSecond/songName 是字面量。
// 显式写出来既符合语义，也让 work/check_refmap_coverage.py 能正确地把本类归为"无需 refmap"。
@Mixin(value = MusicToClientMessage.class, remap = false)
public interface MusicToClientMessageAccessor {
    @Accessor("pos")
    BlockPos ncpb$pos();

    @Accessor("url")
    String ncpb$url();

    @Accessor("rawUrl")
    String ncpb$rawUrl();

    @Accessor("timeSecond")
    int ncpb$timeSecond();

    @Accessor("songName")
    String ncpb$songName();
}
