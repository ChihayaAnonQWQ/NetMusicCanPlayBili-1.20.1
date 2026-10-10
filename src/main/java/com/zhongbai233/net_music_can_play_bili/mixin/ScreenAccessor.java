package com.zhongbai233.net_music_can_play_bili.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 {@link Screen} 的两个私有控件列表。
 *
 * <p><b>1.20.1 移植说明（本类为什么只有 {@code @Accessor} 成员）</b>：
 * 模组需要在 {@code Screen} 实例上添加控件，而原版 {@code addRenderableWidget} 是 protected。
 * 原先用 {@code @Invoker("addRenderableWidget")}，但实测有两条死路：</p>
 * <ol>
 *   <li><b>泛型形态不会被写进 refmap</b>——注解处理器为它生成的条目为零
 *       （对照：{@code ItemInHandRendererMixin} 里非泛型的 {@code @Invoker("renderMapHand")} 有条目）。
 *       Forge 1.20.1 生产运行时方法名是 SRG 的 {@code m_142416_}，缺条目就会在实机解析失败。</li>
 *   <li><b>按擦除描述符写成非泛型会被处理器拒绝</b>：报
 *       {@code Could not locate @Invoker target addRenderableWidget}。</li>
 * </ol>
 * <p>也<b>不能</b>在本接口里放 default 方法来复刻语义：一旦有非 accessor 成员，
 * Mixin 就不再把它当 accessor mixin，而普通接口 mixin 只能以接口为目标，
 * 会报 {@code Targetted type '…Screen' is not an interface}（实测）。</p>
 *
 * <p>所以这里只保留 {@code @Accessor}（已被验证能正常进 refmap，同 {@code ButtonAccessor}），
 * 复刻出来的等价实现放在 {@code bridge/ScreenWidgets}。</p>
 */
@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("children")
    List<GuiEventListener> net_music_can_play_bili$children();

    @Accessor("narratables")
    List<NarratableEntry> net_music_can_play_bili$narratables();
}
