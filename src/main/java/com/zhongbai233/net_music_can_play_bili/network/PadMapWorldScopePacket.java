package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.PadMapClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端下发 Pad 地图缓存作用域；scopeId 是缓存隔离主键，worldName 仅用于调试显示。 */
public record PadMapWorldScopePacket(String worldScopeId, String worldName) {
    private static final int MAX_SCOPE_LENGTH = 256;

    public static PadMapWorldScopePacket decode(FriendlyByteBuf buffer) {
        return new PadMapWorldScopePacket(buffer.readUtf(MAX_SCOPE_LENGTH), buffer.readUtf(MAX_SCOPE_LENGTH));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(worldScopeId, MAX_SCOPE_LENGTH);
        buffer.writeUtf(worldName, MAX_SCOPE_LENGTH);
    }

    public static void handle(PadMapWorldScopePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(
                // TODO(1.20.1): 依赖待移植 — client.PadMapClientHooks
                () -> PadMapClientHooks.setServerWorldScope(payload.worldScopeId(), payload.worldName()));
        context.setPacketHandled(true);
    }
}
