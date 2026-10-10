package com.zhongbai233.net_music_can_play_bili;

import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.bili.BiliAudioResolver;
import com.zhongbai233.net_music_can_play_bili.bili.BiliClientAudioHandlers;
import com.zhongbai233.net_music_can_play_bili.bili.BiliConfig;
import com.zhongbai233.net_music_can_play_bili.client.ModernTurntableClientEvents;
import com.zhongbai233.net_music_can_play_bili.init.ModAttributes;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.init.ModBlocks;
import com.zhongbai233.net_music_can_play_bili.init.ModItems;
import com.zhongbai233.net_music_can_play_bili.init.ModMenus;
import com.zhongbai233.net_music_can_play_bili.network.ModernTurntableNetwork;
import com.zhongbai233.net_music_can_play_bili.media.stream.TempFileByteSpool;
import com.zhongbai233.net_music_can_play_bili.server.NetMusicBiliServerCommands;
import com.zhongbai233.net_music_can_play_bili.server.NetMusicPermissions;
import com.zhongbai233.net_music_can_play_bili.server.AreaAudioListenerSync;
import com.zhongbai233.net_music_can_play_bili.server.PadMapScopeSync;
import com.zhongbai233.net_music_can_play_bili.server.PlaybackAuditManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(NetMusicCanPlayBili.MODID)
public class NetMusicCanPlayBili {
    public static final String MODID = "net_music_can_play_bili";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NetMusicCanPlayBili(IEventBus modEventBus, ModContainer modContainer) {
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(ModernTurntableNetwork::register);
        modEventBus.addListener(this::registerCapabilities);
        MinecraftForge.EVENT_BUS.addListener(NetMusicPermissions::onPermissionGather);
        MinecraftForge.EVENT_BUS.addListener(NetMusicBiliServerCommands::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(PadMapScopeSync::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onServerStopping);
        registerDevelopmentSelfTests();
        MinecraftForge.EVENT_BUS.addListener(PlaybackAuditManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(PlaybackAuditManager::onPlayerLoggedIn);

        modEventBus.addListener(Config::onConfigChanged);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MusicPlayResolverManager.registerResolver(new BiliAudioResolver());
        TempFileByteSpool.cleanupOrphanedSpoolFiles();
        LOGGER.info("BiliAudioResolver registered with NetMusic");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModernTurntableClientEvents.register(modEventBus);
            BiliConfig.load();
            BiliClientAudioHandlers.register();
            // 1.21.1:ClientTooltipComponent 是接口,自定义 tooltip 工厂走 NeoForge 事件注册。
            modEventBus.addListener(
                    net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent.class,
                    event -> event.register(
                            com.zhongbai233.net_music_can_play_bili.client.tooltip.MP4QueueTooltip.class,
                            com.zhongbai233.net_music_can_play_bili.client.tooltip.ClientMP4QueueTooltip::new));
        }
    }

    /**
     * 1.20.1 的能力注册与 1.21.1 不同：
     * <ul>
     *   <li>NeoForge 用 {@code event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, provider)}
     *       一次性把能力挂到「方块实体类型」上；Forge 1.20.1 的 {@link RegisterCapabilitiesEvent}
     *       只有 {@code register(Class)}，它只负责<b>声明</b>某个能力接口会被注册。</li>
     *   <li>真正把能力暴露出去是在方块实体里覆写
     *       {@code BlockEntity#getCapability(Capability, Direction)} —— 见
     *       {@code ModernTurntableBlockEntity#getCapability}。</li>
     * </ul>
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IItemHandler.class);
    }

    private static void registerDevelopmentSelfTests() {
        if (PadDiagnosticsProperties.mapServerSelfTestEnabled()) {
            registerDevelopmentSelfTest(
                    "com.zhongbai233.net_music_can_play_bili.server.PadMapSamplerServerSelfTest",
                    "Pad map server self-test");
        }
        if (Boolean.getBoolean("ncpb.scene_editor.server_self_test")) {
            registerDevelopmentSelfTest(
                    "com.zhongbai233.net_music_can_play_bili.server.SceneEditorDedicatedServerSelfTest",
                    "Scene Editor dedicated-server self-test");
        }
    }

    private static void registerDevelopmentSelfTest(String className, String description) {
        try {
            Class<?> selfTest = Class.forName(className);
            var handler = selfTest.getMethod("onServerStarted",
                    net.minecraftforge.event.server.ServerStartedEvent.class);
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.event.server.ServerStartedEvent.class,
                    event -> invokeDevelopmentSelfTest(handler, event));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to register " + description, ex);
        }
    }

    private static void invokeDevelopmentSelfTest(java.lang.reflect.Method handler,
            net.minecraftforge.event.server.ServerStartedEvent event) {
        try {
            handler.invoke(null, event);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to run Pad map server self-test", ex);
        }
    }

}
