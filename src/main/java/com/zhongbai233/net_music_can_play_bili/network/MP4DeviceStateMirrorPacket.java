package com.zhongbai233.net_music_can_play_bili.network;

 import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 面向当前持有该设备玩家的服务端权威 MP4 设备配置镜像。 */
public record MP4DeviceStateMirrorPacket(UUID deviceId, MP4StatePacket state, long updatedGameTime,
        boolean headphoneLinked, List<ItemStack> queue) {

    public static MP4DeviceStateMirrorPacket decode(FriendlyByteBuf buffer) {
        UUID deviceId = buffer.readUUID();
        MP4StatePacket state = MP4StatePacket.decode(buffer);
        long updatedGameTime = buffer.readLong();
        boolean headphoneLinked = buffer.readBoolean();
        int size = Math.max(0, Math.min(MP4Item.MAX_QUEUE_SIZE, buffer.readVarInt()));
        List<ItemStack> queue = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            queue.add(readOptionalStack(buffer));
        }
        return new MP4DeviceStateMirrorPacket(deviceId, state, updatedGameTime, headphoneLinked,
                List.copyOf(queue));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(deviceId);
        state.encode(buffer);
        buffer.writeLong(updatedGameTime);
        buffer.writeBoolean(headphoneLinked);
        List<ItemStack> safeQueue = queue == null ? List.of() : queue;
        int size = Math.min(MP4Item.MAX_QUEUE_SIZE, safeQueue.size());
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writeOptionalStack(buffer, safeQueue.get(i));
        }
    }

    public static MP4DeviceStateMirrorPacket fromEntry(UUID deviceId, MP4DeviceStateStore.DeviceEntry entry,
            boolean headphoneLinked) {
        return new MP4DeviceStateMirrorPacket(deviceId, MP4StatePacket.fromState(entry.state(), deviceId),
                entry.updatedGameTime(), headphoneLinked, entry.queue());
    }

    public static void handle(MP4DeviceStateMirrorPacket payload,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 1.20.1 已恢复：MP4Client 已迁入工程。
        context.enqueueWork(() -> MP4Client.receiveMirroredState(
                payload.deviceId(), payload.state().toState(), payload.updatedGameTime(),
                payload.headphoneLinked(), payload.queue()));
        context.setPacketHandled(true);
    }

    /**
     * 1.20.1 没有 26.1 的 {@code ItemStack.OPTIONAL_STREAM_CODEC}（该字段 1.20.5 才引入），
     * 这里用布尔前置标记表达「空栈」语义，线上格式与原 optional 编解码一致。
     */
    private static ItemStack readOptionalStack(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readItem() : ItemStack.EMPTY;
    }

    private static void writeOptionalStack(FriendlyByteBuf buffer, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            buffer.writeBoolean(false);
            return;
        }
        buffer.writeBoolean(true);
        buffer.writeItem(stack);
    }
}
