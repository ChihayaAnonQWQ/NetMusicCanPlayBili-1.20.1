package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.item.HolographicGlassesItem;
import com.zhongbai233.net_music_can_play_bili.link.EquippedMediaItems;
import com.zhongbai233.net_music_can_play_bili.link.HolographicGlassesAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 全息眼镜单块屏幕的配置下发。
 * <p>
 * 1.20.1 移植说明：26.1 的 {@code CustomPacketPayload} + {@code StreamCodec} +
 * {@code IPayloadContext} 改为 {@code SimpleChannel} 所需的
 * {@link #decode(FriendlyByteBuf)} / {@link #encode(FriendlyByteBuf)} /
 * {@link #handle(HolographicGlassesConfigPacket, Supplier)} 三件套；
 * {@code context.player()} 对应可空的 {@code context.getSender()}。
 */
public record HolographicGlassesConfigPacket(int screenIndex, float distance, float offsetX, float offsetY,
        float height, float aspect, float roll) {
    public static HolographicGlassesConfigPacket decode(FriendlyByteBuf buf) {
        return new HolographicGlassesConfigPacket(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(screenIndex);
        buf.writeFloat(distance);
        buf.writeFloat(offsetX);
        buf.writeFloat(offsetY);
        buf.writeFloat(height);
        buf.writeFloat(aspect);
        buf.writeFloat(roll);
    }

    public static HolographicGlassesConfigPacket fromConfig(int screenIndex,
            HolographicGlassesItem.ScreenConfig config) {
        return new HolographicGlassesConfigPacket(screenIndex, config.distance(), config.offsetX(), config.offsetY(),
                config.height(), config.aspect(), config.roll());
    }

    private HolographicGlassesItem.ScreenConfig toConfig() {
        return new HolographicGlassesItem.ScreenConfig(distance, offsetX, offsetY, height, aspect, roll);
    }

    public static void handle(HolographicGlassesConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "holographic_glasses_config", 8)) {
                return;
            }
            // TODO(1.20.1): 依赖待移植 — EquippedMediaItems / HolographicGlassesAbility / HolographicGlassesItem
            ItemStack head = EquippedMediaItems.firstHolographicGlasses(player);
            if (!HolographicGlassesAbility.has(head)) {
                return;
            }
            List<HolographicGlassesItem.ScreenConfig> configs = new ArrayList<>(
                    HolographicGlassesItem.readScreenConfigs(head));
            if (payload.screenIndex() < 0 || payload.screenIndex() >= configs.size()) {
                return;
            }
            configs.set(payload.screenIndex(), payload.toConfig());
            HolographicGlassesItem.writeScreenConfigs(head, configs);
            player.getInventory().setChanged();
        });
        context.setPacketHandled(true);
    }
}
