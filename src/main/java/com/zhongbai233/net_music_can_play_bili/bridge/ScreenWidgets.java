package com.zhongbai233.net_music_can_play_bili.bridge;

import com.zhongbai233.net_music_can_play_bili.mixin.ScreenAccessor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/**
 * 在 {@link Screen} 实例上添加控件的 1.20.1 等价实现。
 *
 * <p><b>为什么需要</b>：原版 {@code Screen#addRenderableWidget(T)} 是 protected，
 * 而本模组的 mixin 类只混入 NetMusic 的界面类、不是 {@code Screen} 的子类，
 * 编译期无法直接调用它；{@code @Invoker} 路线又因目标方法是泛型而拿不到 refmap 条目
 * （详见 {@link ScreenAccessor} 的说明）。</p>
 *
 * <p><b>语义依据</b>（对 1.20.1 {@code Screen} 反汇编实证）：
 * <pre>
 *   Screen#addRenderableWidget(T)：
 *       this.renderables.add((Renderable) widget);
 *       this.addWidget(widget);
 *   Screen#addWidget(T)：
 *       this.children.add(widget);
 *       if (widget instanceof NarratableEntry) this.narratables.add(widget);
 *       return widget;
 * </pre>
 * {@code renderables} 在 1.20.1 是 <b>public final</b>，直接访问；
 * {@code children}/{@code narratables} 是 private final，经 {@link ScreenAccessor} 读取。
 * 本方法的泛型上界同时含 {@code Renderable} 与 {@code NarratableEntry}，
 * 所以三个列表都无条件加入，与原实现逐项一致。</p>
 */
public final class ScreenWidgets {
    private ScreenWidgets() {
    }

    /** 等价于 1.20.1 的 {@code Screen#addRenderableWidget(T)}。 */
    public static <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(Screen screen,
            T widget) {
        if (screen == null || widget == null) {
            return widget;
        }
        screen.renderables.add(widget);
        ScreenAccessor access = (ScreenAccessor) (Object) screen;
        access.net_music_can_play_bili$children().add(widget);
        access.net_music_can_play_bili$narratables().add(widget);
        return widget;
    }
}
