package com.zhongbai233.net_music_can_play_bili.client;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.client.renderer.LyricProjectorRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.ModernTurntableRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.SpeakerRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.VideoProjectorRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.ControlConsoleRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.gui.HolographicPreviewPipRenderState;
import com.zhongbai233.net_music_can_play_bili.client.renderer.gui.HolographicPreviewPipRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.item.CuriosHeadGearLayer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.item.MP4ItemScreenRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.item.PadItemScreenRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.HolographicPrivacyOverlay;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.YuvVideoRenderTypes;
import com.zhongbai233.net_music_can_play_bili.gui.MediaToolBindingScreen;
import com.zhongbai233.net_music_can_play_bili.gui.MediaToolReportScreen;
import com.zhongbai233.net_music_can_play_bili.gui.HolographicScreenConfigTestScreen;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.init.ModBlocks;
import com.zhongbai233.net_music_can_play_bili.init.ModMenus;
import com.zhongbai233.net_music_can_play_bili.port.shim.PortPictureInPictureRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import com.zhongbai233.net_music_can_play_bili.util.MathCompat;

public final class ModernTurntableClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ModernTurntableClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModernTurntableClientEvents::registerRenderers);
        modEventBus.addListener(ModernTurntableClientEvents::registerBlockRenderLayers);
        modEventBus.addListener(CuriosHeadGearLayer::register);
        // 1.21.1 没有 RegisterPictureInPictureRenderersEvent;PIP 渲染器直接注册到 shim 注册表。
        PortPictureInPictureRenderer.registerPipRenderer(HolographicPreviewPipRenderState.class,
                HolographicPreviewPipRenderer::new);
        modEventBus.addListener(ModernTurntableClientEvents::registerMenuScreens);
        modEventBus.addListener(HolographicGlassesKeyHandler::register);
        modEventBus.addListener(YuvVideoRenderTypes::registerPipelines);
        MinecraftForge.EVENT_BUS.addListener(ModernTurntableClientEvents::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(ModernTurntableClientEvents::onRenderFrame);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onMovementInput);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onMouseButton);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onInteraction);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onSubmitGeometry);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onLogout);
        MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onClone);
    }

    /** 上一帧的纳秒时间戳，用于自行换算帧间隔（见 {@link #onRenderFrame}）。 */
    private static long lastFrameNanos;

    /**
     * 每帧一次（渲染线程、渲染之前）的钩子。
     *
     * <p>1.20.1 移植说明：1.21.1/NeoForge 的 {@code RenderFrameEvent.Pre} 在 Forge 1.20.1
     * <b>没有对应物</b>。语义最近的是 {@link TickEvent.RenderTickEvent} 的 START 阶段：
     * 它同样每帧触发一次、发生在渲染之前、且运行在客户端（渲染）线程上。</p>
     *
     * <p>原实现用 {@code DeltaTracker#getRealtimeDeltaTicks()}（距上一帧的真实耗时，以 tick 计）；
     * 1.20.1 没有 DeltaTracker，这里改用 {@link Util#getNanos()} 自行换算成秒，
     * 与原语义等价（真实帧间隔、单位秒、上限 0.1s）。</p>
     */
    private static void onRenderFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof HolographicScreenConfigTestScreen screen) {
            long now = Util.getNanos();
            double deltaSeconds = lastFrameNanos == 0L ? 0.0D : (now - lastFrameNanos) / 1.0E9D;
            lastFrameNanos = now;
            screen.advanceCameraFrame(MathCompat.clamp(deltaSeconds, 0.0D, 0.1D));
        }
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HolographicPrivacyOverlay.release();
    }

    /**
     * 菜单 → 界面 的绑定。
     *
     * <p>1.20.1 移植说明：NeoForge 的 {@code RegisterMenuScreensEvent} 在 Forge 1.20.1
     * <b>没有对应物</b>（1.20.1 既没有 {@code MenuScreensEvent} 也没有该事件类）。
     * 1.20.1 的标准做法是在 {@link FMLClientSetupEvent} 里用
     * {@link MenuScreens#register} 手动绑定 —— 这也是 Forge 官方 MDK 的写法。</p>
     */
    private static void registerMenuScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.MEDIA_TOOL_BINDING.get(), MediaToolBindingScreen::new);
            MenuScreens.register(ModMenus.MEDIA_TOOL_REPORT.get(), MediaToolReportScreen::new);
        });
    }

    /**
     * 1.21.1 没有 26.x 的「按材质 alpha 自动推断渲染层」机制:未显式注册的方块一律走 solid 层,
     * alpha=0 的像素被画成黑色,0&lt;alpha&lt;255 的半透明像素被当作不透明处理。
     * 现代唱片盒的玻璃罩恰好由 846 个半透明像素构成,因此在 1.21.1 上表现为完全不透明的实心罩。
     * 上游 26.x 由材质 flags 从纹理自动推断,这里补齐等价的显式声明。
     */
    private static void registerBlockRenderLayers(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MODERN_TURNTABLE.get(), RenderType.translucent());
            // 投影仪同源:projector_activated 模型的面引用了 390 个 alpha=0 与 96 个半透明像素
            // (未激活态 818 个面全部不透明),因此整块声明为 translucent 才能与 26.x 的自动推断等价。
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.VIDEO_PROJECTOR.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LYRIC_PROJECTOR.get(), RenderType.translucent());
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MODERN_TURNTABLE.get(), ModernTurntableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LYRIC_PROJECTOR.get(), LyricProjectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VIDEO_PROJECTOR.get(), VideoProjectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CONTROL_CONSOLE.get(), ControlConsoleRenderer::new);
        warmupClientResources();
    }

    private static void warmupClientResources() {
        Minecraft.getInstance().execute(() -> {
            try {
                MP4ItemScreenRenderer.warmup();
                PadItemScreenRenderer.warmup();
                LOGGER.debug("MP4/Pad handheld GUI resources warmed up");
            } catch (Exception e) {
                LOGGER.warn("MP4/Pad handheld GUI resource warmup failed; falling back to lazy initialization", e);
            }
        });
    }
}