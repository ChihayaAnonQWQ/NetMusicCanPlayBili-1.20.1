package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import com.zhongbai233.net_music_can_play_bili.item.HolographicGlassesItem;
import com.zhongbai233.net_music_can_play_bili.item.InvisibleHeadphonesItem;
import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import com.zhongbai233.net_music_can_play_bili.item.MediaManagementToolItem;
import com.zhongbai233.net_music_can_play_bili.item.PadItem;
import com.zhongbai233.net_music_can_play_bili.link.HeadphoneAbility;
import com.zhongbai233.net_music_can_play_bili.link.HolographicGlassesAbility;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;

/**
 * 物品与创造模式标签页注册。
 *
 * <p>1.20.1 移植说明（相对 1.21.1 / NeoForge 的差异）：
 * <ul>
 *   <li>{@code DeferredRegister.Items} 与 {@code createItems(...)} 是 NeoForge 扩展；
 *       1.20.1 统一用 {@code DeferredRegister.create(ForgeRegistries.ITEMS, MODID)}
 *       配合 {@link RegistryObject}。</li>
 *   <li>{@code DeferredItem<T>} → {@link RegistryObject}{@code <T>}；
 *       {@code registerSimpleBlockItem(String, RegistryObject<Block>)} 与
 *       {@code registerItem(String, Function<Item.Properties, ? extends Item>, Item.Properties)}
 *       都是 NeoForge 专有，1.20.1 一律用普通 {@code register(name, supplier)}。</li>
 *   <li><b>装备属性</b>：1.21.1 用 {@code Item.Properties#attributes(ItemAttributeModifiers)}
 *       声明穿戴时生效的属性修饰符；1.20.1 没有 {@code ItemAttributeModifiers} /
 *       {@code EquipmentSlotGroup}，等价能力由物品类覆写
 *       {@code Item#getDefaultAttributeModifiers(EquipmentSlot)} 提供。
 *       因此耳机/全息眼镜的属性修饰符已下沉到
 *       {@link InvisibleHeadphonesItem} 与 {@link HolographicGlassesItem}。</li>
 *   <li><b>附魔书条目</b>：1.21.1 通过
 *       {@code parameters.holders().lookupOrThrow(Registries.ENCHANTMENT)} 查询动态注册表。
 *       1.20.1 的附魔不在数据包注册表查询面内，这里改用 Forge 注册表
 *       {@link ForgeRegistries#ENCHANTMENTS} 直接取值；取不到（没有对应数据包）时
 *       <b>静默跳过</b>，不影响标签页构建。附魔本身与耳机功能无关
 *       —— 耳机是否生效取决于物品的默认属性修饰符。</li>
 * </ul>
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            NetMusicCanPlayBili.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            NetMusicCanPlayBili.MODID);

    public static final RegistryObject<BlockItem> MODERN_TURNTABLE = ITEMS.register("modern_turntable",
            () -> new BlockItem(ModBlocks.MODERN_TURNTABLE.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> LYRIC_PROJECTOR = ITEMS.register("lyric_projector",
            () -> new BlockItem(ModBlocks.LYRIC_PROJECTOR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> VIDEO_PROJECTOR = ITEMS.register("video_projector",
            () -> new BlockItem(ModBlocks.VIDEO_PROJECTOR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> SPEAKER = ITEMS.register("speaker",
            () -> new BlockItem(ModBlocks.SPEAKER.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> LIVE_STREAMER = ITEMS.register("live_streamer",
            () -> new BlockItem(ModBlocks.LIVE_STREAMER.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> CONTROL_CONSOLE = ITEMS.register("control_console",
            () -> new BlockItem(ModBlocks.CONTROL_CONSOLE.get(), new Item.Properties()));

    public static final RegistryObject<MP4Item> MP4 = ITEMS.register("mp4",
            () -> new MP4Item(new Item.Properties()));

    public static final RegistryObject<PadItem> PAD = ITEMS.register("pad",
            () -> new PadItem(new Item.Properties()));

    public static final RegistryObject<MediaManagementToolItem> MEDIA_MANAGEMENT_TOOL = ITEMS.register(
            "media_management_tool",
            () -> new MediaManagementToolItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<InvisibleHeadphonesItem> INVISIBLE_HEADPHONES = ITEMS.register(
            "invisible_headphones",
            () -> new InvisibleHeadphonesItem(new Item.Properties()));

    public static final RegistryObject<InvisibleHeadphonesItem> CAT_HEADPHONES = ITEMS.register(
            "cat_headphones",
            () -> new InvisibleHeadphonesItem(new Item.Properties()));

    public static final RegistryObject<HolographicGlassesItem> HOLOGRAPHIC_GLASSES = ITEMS.register(
            "holographic_glasses",
            () -> new HolographicGlassesItem(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.net_music_can_play_bili"))
                    .icon(() -> new ItemStack(MODERN_TURNTABLE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(MODERN_TURNTABLE.get());
                        output.accept(LYRIC_PROJECTOR.get());
                        output.accept(VIDEO_PROJECTOR.get());
                        output.accept(SPEAKER.get());
                        output.accept(LIVE_STREAMER.get());
                        output.accept(CONTROL_CONSOLE.get());
                        output.accept(MP4.get());
                        output.accept(PAD.get());
                        output.accept(MEDIA_MANAGEMENT_TOOL.get());
                        output.accept(INVISIBLE_HEADPHONES.get());
                        output.accept(CAT_HEADPHONES.get());
                        output.accept(HOLOGRAPHIC_GLASSES.get());
                        acceptEnchantedBook(output, HeadphoneAbility.HEADPHONES_KEY);
                        acceptEnchantedBook(output, HolographicGlassesAbility.HOLOGRAPHIC_GLASSES_KEY);
                    })
                    .build());

    /**
     * 把指定附魔以「附魔书」形式加入创造模式标签页。
     *
     * <p>1.20.1 没有数据组件：附魔书的附魔存在 NBT 的 {@code StoredEnchantments} 里，
     * 由 {@link EnchantmentHelper#setEnchantments} 负责按物品类型写入正确的位置。
     * 若该附魔未注册（例如缺少提供它的数据包），则跳过而不报错。</p>
     */
    private static void acceptEnchantedBook(CreativeModeTab.Output output, ResourceKey<Enchantment> enchantmentKey) {
        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchantmentKey.location());
        if (enchantment == null) {
            return;
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(Collections.singletonMap(enchantment, 1), book);
        output.accept(book);
    }

    private ModItems() {
    }
}
