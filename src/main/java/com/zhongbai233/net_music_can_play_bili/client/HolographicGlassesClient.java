package com.zhongbai233.net_music_can_play_bili.client;

import com.zhongbai233.net_music_can_play_bili.client.renderer.ClientDisplayProperties;
import com.zhongbai233.net_music_can_play_bili.item.HolographicGlassesItem;
import com.zhongbai233.net_music_can_play_bili.link.EquippedMediaItems;
import com.zhongbai233.net_music_can_play_bili.link.HolographicGlassesAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/** 客户端本地的全息眼镜隐私保护状态。 */
public final class HolographicGlassesClient {
    private HolographicGlassesClient() {
    }

    public static boolean active() {
        Player player = Minecraft.getInstance().player;
        return player != null && HolographicGlassesAbility.has(EquippedMediaItems.firstHolographicGlasses(player));
    }

    public static boolean shouldHideProjectorVideos() {
        return active();
    }

    public static UUID boundMp4DeviceId() {
        List<HolographicGlassesItem.ScreenBinding> bindings = screenBindings();
        return bindings.isEmpty() ? null : bindings.get(0).deviceId();
    }

    /** 上一次解析所用的（物品栈身份, NBT 身份）与其结果。 */
    private static ItemStack cachedBindingStack;
    private static CompoundTag cachedBindingData;
    private static List<HolographicGlassesItem.ScreenBinding> cachedBindings = List.of();

    /**
     * 读取眼镜上绑定的屏幕列表（带记忆化）。
     *
     * <p>{@code readScreenBindings} 会做一次整棵 NBT 深拷贝（{@code customData.copy()}）并重建
     * record 列表，而本方法在渲染线程上每帧、每个视频会话都会调用一次（
     * {@code VideoPlaybackInstance#hasHolographicTurntableConsumer} 由
     * {@code VideoPlaybackPresentation#submit} 无条件调用）。</p>
     *
     * <p>缓存键用两者<b>身份</b>而非内容：{@code firstEquipped} 返回的是背包/Curios 里的实时栈实例，
     * 身份在栈未被替换时稳定。因此身份比较既能正确失效，又只有 O(1) 成本——若改为比较
     * NBT 内容，递归遍历的代价本身就和要省掉的深拷贝同量级。</p>
     *
     * <p><b>1.20.1 差异</b>：1.21.1 里绑定写入走
     * {@code stack.set(DataComponents.CUSTOM_DATA, ...)}，必然换成新的组件实例；
     * 而 1.20.1 没有数据组件，NBT 写在 {@code ItemStack#getOrCreateTag()} 上，
     * <b>原地修改不会改变 NBT 实例身份</b>。这里之所以仍然安全：绑定只由服务端
     * （{@code MediaEquipmentBindingService}）写入，客户端会通过槽位同步拿到
     * <b>新的 ItemStack 实例</b>，栈身份变化同样会使缓存失效。
     * 若将来出现客户端本地原地写绑定的路径，必须在这里补显式失效。</p>
     */
    public static List<HolographicGlassesItem.ScreenBinding> screenBindings() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        ItemStack head = EquippedMediaItems.firstHolographicGlasses(player);
        if (!HolographicGlassesAbility.has(head)) {
            return List.of();
        }
        CompoundTag data = head.getTag();
        if (head == cachedBindingStack && data == cachedBindingData) {
            return cachedBindings;
        }
        List<HolographicGlassesItem.ScreenBinding> bindings = HolographicGlassesItem.readScreenBindings(head);
        cachedBindingStack = head;
        cachedBindingData = data;
        cachedBindings = bindings;
        return bindings;
    }

    public static boolean handlesTurntable(BlockPos turntablePos) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> dimension = minecraft.level != null ? minecraft.level.dimension() : null;
        return handlesTurntable(dimension, turntablePos);
    }

    public static boolean handlesTurntable(ResourceKey<Level> dimension, BlockPos turntablePos) {
        if (!ClientDisplayProperties.holographicWorldScreenEnabled()
                || dimension == null || turntablePos == null) {
            return false;
        }
        for (HolographicGlassesItem.ScreenBinding binding : screenBindings()) {
            if (binding.source() != null && binding.source().isTurntable()
                    && dimension.equals(binding.source().dimension())
                    && turntablePos.equals(binding.source().pos())) {
                return true;
            }
        }
        return false;
    }

    public static HolographicGlassesItem.ScreenConfig screenConfig() {
        List<HolographicGlassesItem.ScreenBinding> bindings = screenBindings();
        return bindings.isEmpty() ? HolographicGlassesItem.defaultScreenConfig() : bindings.get(0).config();
    }
}
