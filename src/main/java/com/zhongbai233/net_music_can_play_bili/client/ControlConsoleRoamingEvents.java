package com.zhongbai233.net_music_can_play_bili.client;

import com.zhongbai233.net_music_can_play_bili.port.shim.PortSubmitNodeCollector;
import com.zhongbai233.net_music_can_play_bili.port.shim.PortWorldRenderEvents;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** 灵魂漫游的 NeoForge 事件桥接。 */
public final class ControlConsoleRoamingEvents {
    private ControlConsoleRoamingEvents() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ControlConsoleRoamingSession.tick();
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        ControlConsoleRoamingSession.suppressPlayerInput(event.getInput());
    }

    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (ControlConsoleRoamingSession.handleMouseButton(event.getButton(), event.getAction())) {
            event.setCanceled(true);
        }
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (ControlConsoleRoamingSession.isActive()
                && (event.isAttack() || event.isUseItem() || event.isPickBlock())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (ControlConsoleRoamingSession.isActive()) {
            ControlConsoleRoamingSession.adjustFlyingSpeed(event.getScrollDeltaY());
            event.setCanceled(true);
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        ControlConsoleRoamingSession.drawHud(event.getGuiGraphics());
    }

    public static void onSubmitGeometry(RenderLevelStageEvent event) {
        if (!PortWorldRenderEvents.isStage(event, RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)) {
            return;
        }
        PortSubmitNodeCollector collector = PortWorldRenderEvents.begin();
        try {
            ControlConsoleRoamingSession.submitGeometry(collector);
        } finally {
            PortWorldRenderEvents.end(collector);
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ControlConsoleRoamingSession.stop(false);
    }

    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        ControlConsoleRoamingSession.stop(false);
    }
}