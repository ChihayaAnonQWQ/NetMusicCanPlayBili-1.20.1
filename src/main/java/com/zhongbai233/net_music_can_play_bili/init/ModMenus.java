package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import com.zhongbai233.net_music_can_play_bili.menu.MediaToolBindingMenu;
import com.zhongbai233.net_music_can_play_bili.menu.MediaToolReportMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 菜单类型注册。
 *
 * <p>1.20.1 移植说明：NeoForge 的 {@code DeferredHolder<MenuType<?>, MenuType<X>>}
 * 在 Forge 1.20.1 是单类型参数的 {@link RegistryObject}{@code <MenuType<X>>}。
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU,
            NetMusicCanPlayBili.MODID);

    public static final RegistryObject<MenuType<MediaToolBindingMenu>> MEDIA_TOOL_BINDING = MENU_TYPES
            .register("media_tool_binding",
                    () -> new MenuType<>((IContainerFactory<MediaToolBindingMenu>) MediaToolBindingMenu::new,
                            FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<MenuType<MediaToolReportMenu>> MEDIA_TOOL_REPORT = MENU_TYPES
            .register("media_tool_report",
                    () -> new MenuType<>((IContainerFactory<MediaToolReportMenu>) MediaToolReportMenu::new,
                            FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
