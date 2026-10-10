package com.zhongbai233.net_music_can_play_bili.link;

import com.zhongbai233.net_music_can_play_bili.media.audio.AudioPlaybackRange;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 存储在音频路由物品上的共享持久绑定数据。
 * <p>
 * 本类统一管理耳机绑定的 NBT 结构，避免物品交互、客户端路由和服务端索引直接依赖原始 NBT 键。
 * </p>
 * <p>
 * <b>1.20.1 移植说明</b>：原始（1.21.1）版本用数据组件 {@code DataComponents.CUSTOM_DATA}
 * 承载这些键，写入形如
 * {@code stack.update(DataComponents.CUSTOM_DATA, new CompoundTag(), cd -> cd.update(tag -> {...}))}。
 * 1.20.1 没有数据组件，{@code CUSTOM_DATA} 就是物品自身的 NBT（{@link ItemStack#getTag()} /
 * {@link ItemStack#getOrCreateTag()}），故写操作直接取 {@code getOrCreateTag()} 后就地 put/remove，
 * 与原来的 update 等价。唯一区别：1.21.1 的 {@code update} 在结果为空时会自动移除组件，
 * 1.20.1 需要手工 {@code stack.setTag(null)}（见 {@link #dropIfEmpty}）。
 * </p>
 */
public final class AudioLinkData {
    public static final double MP4_HEADPHONE_RANGE_SQUARED = AudioPlaybackRange.HEADPHONE_LINK_DISTANCE_SQUARED;

    private static final String HEADPHONE_TURNTABLE_X = "headphones_turntable_x";
    private static final String HEADPHONE_TURNTABLE_Y = "headphones_turntable_y";
    private static final String HEADPHONE_TURNTABLE_Z = "headphones_turntable_z";
    private static final String HEADPHONE_MP4 = "headphones_mp4";
    private static final String HEADPHONE_MEDIA_DEVICE = "headphones_media_device";

    private AudioLinkData() {
    }

    public static void writeHeadphoneTurntable(ItemStack stack, BlockPos pos) {
        if (stack.isEmpty() || pos == null) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(HEADPHONE_TURNTABLE_X, pos.getX());
        tag.putInt(HEADPHONE_TURNTABLE_Y, pos.getY());
        tag.putInt(HEADPHONE_TURNTABLE_Z, pos.getZ());
    }

    @Nullable
    public static BlockPos readHeadphoneTurntable(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        if (tag == null || !tag.contains(HEADPHONE_TURNTABLE_X) || !tag.contains(HEADPHONE_TURNTABLE_Y)
                || !tag.contains(HEADPHONE_TURNTABLE_Z)) {
            return null;
        }
        return new BlockPos(LinkHelper.getIntOr(tag, HEADPHONE_TURNTABLE_X, 0), LinkHelper.getIntOr(tag, HEADPHONE_TURNTABLE_Y, 0),
                LinkHelper.getIntOr(tag, HEADPHONE_TURNTABLE_Z, 0));
    }

    public static void writeHeadphoneMp4(ItemStack stack, UUID deviceId) {
        writeHeadphoneMediaDevice(stack, deviceId);
    }

    public static void writeHeadphoneMediaDevice(ItemStack stack, UUID deviceId) {
        if (stack.isEmpty() || deviceId == null) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(HEADPHONE_MP4, deviceId.toString());
        tag.putString(HEADPHONE_MEDIA_DEVICE, deviceId.toString());
    }

    @Nullable
    public static UUID readHeadphoneMp4(ItemStack stack) {
        return readHeadphoneMediaDevice(stack);
    }

    @Nullable
    public static UUID readHeadphoneMediaDevice(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        String value = tag != null ? tag.getString(HEADPHONE_MEDIA_DEVICE) : "";
        if (value.isBlank() && tag != null) {
            value = tag.getString(HEADPHONE_MP4);
        }
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void clearHeadphoneMp4(ItemStack stack) {
        clearHeadphoneMediaDevice(stack);
    }

    public static void clearHeadphoneMediaDevice(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(HEADPHONE_MP4);
        tag.remove(HEADPHONE_MEDIA_DEVICE);
        dropIfEmpty(stack, tag);
    }

    public static void clearHeadphoneTurntable(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(HEADPHONE_TURNTABLE_X);
        tag.remove(HEADPHONE_TURNTABLE_Y);
        tag.remove(HEADPHONE_TURNTABLE_Z);
        dropIfEmpty(stack, tag);
    }

    public static int clearHeadphoneLinks(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int count = 0;
        if (readHeadphoneTurntable(stack) != null) {
            count++;
        }
        if (readHeadphoneMp4(stack) != null) {
            count++;
        }
        if (count <= 0) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(HEADPHONE_TURNTABLE_X);
            tag.remove(HEADPHONE_TURNTABLE_Y);
            tag.remove(HEADPHONE_TURNTABLE_Z);
            tag.remove(HEADPHONE_MP4);
            tag.remove(HEADPHONE_MEDIA_DEVICE);
            dropIfEmpty(stack, tag);
        }
        return count;
    }

    public static boolean headphoneLinkedToMp4(ItemStack stack, UUID deviceId) {
        return headphoneLinkedToMediaDevice(stack, deviceId);
    }

    public static boolean headphoneLinkedToMediaDevice(ItemStack stack, UUID deviceId) {
        return deviceId != null && deviceId.equals(readHeadphoneMediaDevice(stack));
    }

    @Nullable
    private static CompoundTag customTag(ItemStack stack) {
        CompoundTag customData = stack.getTag();
        return customData != null && !customData.isEmpty() ? customData.copy() : null;
    }

    /**
     * 1.20.1 专属收尾：1.21.1 的 {@code ItemStack#update} 在更新结果为空时会自动移除
     * {@code CUSTOM_DATA} 组件；1.20.1 的空 {@link CompoundTag} 会留在物品上，
     * 主动置空以免污染工具提示与网络同步（{@code ItemStack#setTag(null)} 与"无 tag"序列化等价）。
     */
    private static void dropIfEmpty(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }
}
