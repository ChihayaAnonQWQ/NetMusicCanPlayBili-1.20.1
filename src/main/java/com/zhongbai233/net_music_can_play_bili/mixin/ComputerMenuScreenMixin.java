package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.client.gui.ComputerMenuScreen;
import com.zhongbai233.net_music_can_play_bili.bili.BiliConfig;
import com.zhongbai233.net_music_can_play_bili.bridge.ScreenWidgets;
import com.zhongbai233.net_music_can_play_bili.gui.BiliQrLoginScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 NetMusic 电脑加一个 "B站登录" 按钮
 */
@Mixin(value = ComputerMenuScreen.class, remap = false)
public abstract class ComputerMenuScreenMixin {

    @Shadow
    private EditBox urlTextField;

    @Unique
    private Button net_music_can_play_bili$loginButton;

    @Unique
    private Button net_music_can_play_bili$dolbyButton;

    // remap = true 必须显式写：`init` 来自 MC 的 Screen（生产名 m_7856_），
    // 而本 mixin 类级是 remap = false（目标是前置模组 NetMusic 的类），
    // mixin 的类级 remap 会被成员继承（注解处理器字节码实证），不显式覆盖就不会生成 refmap 条目。
    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void net_music_can_play_bili$initLoginButton(CallbackInfo ci) {
        this.net_music_can_play_bili$loginButton = Button.builder(
                Component.literal("B站登录"),
                btn -> Minecraft.getInstance().setScreen(new BiliQrLoginScreen()))
                .pos(this.urlTextField.getX(),
                        this.urlTextField.getY() + 80)
                .size(60, 18)
                .build();

        this.net_music_can_play_bili$dolbyButton = Button.builder(
                Component.literal(BiliConfig.dolbyEnabled ? "杜比:开" : "杜比:关"),
                btn -> {
                    BiliConfig.dolbyEnabled = !BiliConfig.dolbyEnabled;
                    BiliConfig.save();
                    btn.setMessage(Component.literal(BiliConfig.dolbyEnabled ? "杜比:开" : "杜比:关"));
                })
                .pos(this.urlTextField.getX() + 64,
                        this.urlTextField.getY() + 80)
                .size(60, 18)
                .build();

        // 1.20.1：Screen#addRenderableWidget 是 protected，且其 @Invoker 形态拿不到 refmap 条目
        // （泛型目标不被注解处理器映射），故走 ScreenWidgets 复刻的等价实现。
        ScreenWidgets.addRenderableWidget((Screen) (Object) this, this.net_music_can_play_bili$loginButton);
        ScreenWidgets.addRenderableWidget((Screen) (Object) this, this.net_music_can_play_bili$dolbyButton);
    }
}
