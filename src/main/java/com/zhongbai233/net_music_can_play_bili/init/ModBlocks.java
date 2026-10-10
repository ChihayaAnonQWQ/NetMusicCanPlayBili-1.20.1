package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import com.zhongbai233.net_music_can_play_bili.block.LiveStreamerBlock;
import com.zhongbai233.net_music_can_play_bili.block.ControlConsoleBlock;
import com.zhongbai233.net_music_can_play_bili.block.LyricProjectorBlock;
import com.zhongbai233.net_music_can_play_bili.block.ModernTurntableBlock;
import com.zhongbai233.net_music_can_play_bili.block.SpeakerBlock;
import com.zhongbai233.net_music_can_play_bili.block.VideoProjectorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册。
 *
 * <p>1.20.1 移植说明（相对 1.21.1 / NeoForge 的差异）：
 * <ul>
 *   <li>{@code DeferredRegister.Blocks} 与 {@code DeferredRegister.createBlocks(...)} 是
 *       NeoForge 的扩展类型；Forge 1.20.1 统一用
 *       {@code DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)} 配合
 *       {@link RegistryObject}。</li>
 *   <li>{@code DeferredBlock<T>} → {@link RegistryObject}{@code <T>}。</li>
 *   <li>NeoForge 的 {@code Blocks#register(String, Function<ResourceLocation, ? extends Block>)}
 *       会把方块自身的 id 传给工厂（1.21.2+ 需要它来设置 {@code Properties#setId}）。
 *       Forge 1.20.1 的 {@code register} 只有 {@code Supplier} 重载，且
 *       {@code BlockBehaviour.Properties} 不需要 id，故统一改回
 *       {@code () -> new XxxBlock(BlockBehaviour.Properties.of())}。</li>
 * </ul>
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            NetMusicCanPlayBili.MODID);

    public static final RegistryObject<Block> MODERN_TURNTABLE = BLOCKS.register(
            "modern_turntable",
            () -> new ModernTurntableBlock(BlockBehaviour.Properties.of()));

    public static final RegistryObject<Block> LYRIC_PROJECTOR = BLOCKS.register(
            "lyric_projector",
            () -> new LyricProjectorBlock(BlockBehaviour.Properties.of()));

    public static final RegistryObject<Block> VIDEO_PROJECTOR = BLOCKS.register(
            "video_projector",
            () -> new VideoProjectorBlock(BlockBehaviour.Properties.of()));

    public static final RegistryObject<Block> SPEAKER = BLOCKS.register(
            "speaker",
            () -> new SpeakerBlock(BlockBehaviour.Properties.of()));

    public static final RegistryObject<Block> LIVE_STREAMER = BLOCKS.register(
            "live_streamer",
            () -> new LiveStreamerBlock(BlockBehaviour.Properties.of()));

    public static final RegistryObject<Block> CONTROL_CONSOLE = BLOCKS.register(
            "control_console",
            () -> new ControlConsoleBlock(BlockBehaviour.Properties.of()));

    private ModBlocks() {
    }
}
