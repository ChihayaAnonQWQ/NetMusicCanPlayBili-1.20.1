package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.LyricProjectorBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.SpeakerBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.VideoProjectorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块实体类型注册。
 *
 * <p>1.20.1 移植说明（相对 1.21.1 / NeoForge 的差异）：
 * <ul>
 *   <li>NeoForge 的 {@code DeferredHolder<R, T extends R>} 是「注册表根类型 + 具体类型」两参数；
 *       Forge 1.20.1 的 {@code DeferredRegister.register(...)} 返回单类型参数的
 *       {@link RegistryObject}{@code <T>}，故
 *       {@code DeferredHolder<BlockEntityType<?>, BlockEntityType<X>>} →
 *       {@code RegistryObject<BlockEntityType<X>>}。</li>
 *   <li>{@code BlockEntityType.Builder#build(Type)} 在 1.20.1 仍需要一个 datafixer 类型实参，
 *       传 {@code null} 即可（1.20.5+ 才去掉该参数）。</li>
 * </ul>
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, NetMusicCanPlayBili.MODID);

    public static final RegistryObject<BlockEntityType<ModernTurntableBlockEntity>> MODERN_TURNTABLE = BLOCK_ENTITY_TYPES
            .register(
                    "modern_turntable",
                    () -> BlockEntityType.Builder.of(
                            ModernTurntableBlockEntity::new,
                            ModBlocks.MODERN_TURNTABLE.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<LyricProjectorBlockEntity>> LYRIC_PROJECTOR = BLOCK_ENTITY_TYPES
            .register(
                    "lyric_projector",
                    () -> BlockEntityType.Builder.of(
                            LyricProjectorBlockEntity::new,
                            ModBlocks.LYRIC_PROJECTOR.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<VideoProjectorBlockEntity>> VIDEO_PROJECTOR = BLOCK_ENTITY_TYPES
            .register(
                    "video_projector",
                    () -> BlockEntityType.Builder.of(
                            VideoProjectorBlockEntity::new,
                            ModBlocks.VIDEO_PROJECTOR.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<SpeakerBlockEntity>> SPEAKER = BLOCK_ENTITY_TYPES
            .register(
                    "speaker",
                    () -> BlockEntityType.Builder.of(
                            SpeakerBlockEntity::new,
                            ModBlocks.SPEAKER.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<LiveStreamerBlockEntity>> LIVE_STREAMER = BLOCK_ENTITY_TYPES
            .register(
                    "live_streamer",
                    () -> BlockEntityType.Builder.of(
                            LiveStreamerBlockEntity::new,
                            ModBlocks.LIVE_STREAMER.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<ControlConsoleBlockEntity>> CONTROL_CONSOLE = BLOCK_ENTITY_TYPES
            .register(
                    "control_console",
                    () -> BlockEntityType.Builder.of(
                            ControlConsoleBlockEntity::new,
                            ModBlocks.CONTROL_CONSOLE.get())
                            .build(null));

    private ModBlockEntities() {
    }
}
