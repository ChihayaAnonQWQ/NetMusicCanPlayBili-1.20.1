package com.zhongbai233.net_music_can_play_bili.network;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.item.PadItem;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadDocument;
import com.zhongbai233.net_music_can_play_bili.media.sync.MonotonicMediaClock;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/** Creates a locked Pad copy that shares the draft Pad's device id. */
public record PadPublishPacket(UUID deviceId) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static PadPublishPacket decode(FriendlyByteBuf buffer) {
        return new PadPublishPacket(buffer.readUUID());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(deviceId);
    }

    public static void handle(PadPublishPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || payload.deviceId() == null) {
                return;
            }
            if (!NetworkRateLimiter.allow(player.getUUID(), "pad_publish", 2)) {
                LOGGER.debug("丢弃过频 Pad 发布包: player={} device={}", player.getUUID(), payload.deviceId());
                return;
            }
            ItemStack draft = PadItem.findAllByDeviceId(player, payload.deviceId()).stream()
                    .filter(PadItem::isPad)
                    .filter(stack -> !PadItem.readLocked(stack))
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            if (!PadItem.isPad(draft)) {
                return;
            }
            PadDocument draftDocument = PadItem.readLegacyDocument(draft);
            draftDocument = (draftDocument == null ? PadDocument.DEFAULT : draftDocument).copyWithLocked(false);
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                draftDocument = PadDocumentStore.getOrCreate(level, payload.deviceId(), draft).copyWithLocked(false);
                PadDocumentStore.update(level, payload.deviceId(), draftDocument);
            }
            PadItem.writeDeviceId(draft, payload.deviceId());
            PadItem.writeDocument(draft, draftDocument.copyWithLocked(false));
            PadItem.writeDocumentToLockedCopies(player, payload.deviceId(), draftDocument);

            ItemStack published = draft.copyWithCount(1);
            PadItem.writeDeviceId(published, payload.deviceId());
            PadItem.writeDocument(published, draftDocument.copyWithLocked(true));
            if (!player.getInventory().add(published)) {
                player.drop(published, false);
            }
            PadDeviceHolderTracker.invalidate(payload.deviceId());
            // TODO(1.20.1): 依赖待移植 — 发送端待接 SimpleChannel：ModernTurntableNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)
            NcpbNetworkDispatch.sendToPlayer(player, new PadStateMirrorPacket(payload.deviceId(), draftDocument,
                    MonotonicMediaClock.nowTick()));
        });
        context.setPacketHandled(true);
    }
}
