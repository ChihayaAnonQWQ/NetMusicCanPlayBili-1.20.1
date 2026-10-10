package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 本模组的网络通道注册。
 * <p>
 * 1.20.1 移植说明：原始实现面向 26.1 的
 * {@code RegisterPayloadHandlersEvent} + {@code PayloadRegistrar}，以
 * {@code playToServer/playToClient(TYPE, STREAM_CODEC, handler)} 注册可自描述的 payload。
 * 1.20.1 使用 {@link SimpleChannel}：数据包由「类 + 通道内唯一数字 id + 方向」标识，
 * 编解码与处理函数在 {@code messageBuilder} 上显式声明。
 * <p>
 * 协议版本沿用原始实现的 {@code "6"}。
 * <p>
 * id 分配顺序即注册顺序，客户端与服务端必须完全一致，因此**不要**在中间插入新包，
 * 新包一律追加到对应方向的末尾。
 */
public final class ModernTurntableNetwork {
    /** 与原始实现一致的协议版本号。 */
    private static final String PROTOCOL_VERSION = "6";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(NetMusicCanPlayBili.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private ModernTurntableNetwork() {
    }

    /**
     * 在**模组构造期**调用，初始化网络层。
     *
     * <p><b>为什么必须有这个方法（实测崩溃修复）</b>：
     * Forge 只在模组构造阶段允许创建 {@code SimpleChannel}。若本类直到运行时才被首次引用
     * （例如服务端 tick 里发第一个包），通道创建会晚于注册窗口并抛出：
     * <pre>
     * java.lang.ExceptionInInitializerError
     * Caused by: java.lang.IllegalArgumentException: Registration of impl channels is locked
     *     at net.minecraftforge.network.NetworkRegistry.createInstance
     *     at ...ModernTurntableNetwork.&lt;clinit&gt;
     * </pre>
     * 本方法通过**显式触碰 {@link #CHANNEL}** 强制本类在构造期完成静态初始化；
     * 同时挂上 {@code FMLCommonSetupEvent} 以注册各个数据包
     * （原实现只有 {@link #register()}，且全工程无人调用它）。
     */
    public static void init(net.minecraftforge.eventbus.api.IEventBus modEventBus) {
        // 关键：显式引用 CHANNEL，触发 <clinit>，使通道在构造期就被创建。
        java.util.Objects.requireNonNull(CHANNEL, "ModernTurntableNetwork.CHANNEL");
        modEventBus.addListener(ModernTurntableNetwork::onCommonSetup);
    }

    private static void onCommonSetup(
            net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // 数据包注册放在 enqueueWork 里，确保工作线程上执行。
        event.enqueueWork(ModernTurntableNetwork::register);
    }

    /** 注册全部数据包。由 {@link #init} 挂在 {@code FMLCommonSetupEvent} 上调用。 */
    public static void register() {
        int id = 0;

        // ─── 客户端 → 服务端 ───
        id = toServer(id, ModernTurntableControlPacket.class,
                ModernTurntableControlPacket::encode, ModernTurntableControlPacket::decode,
                ModernTurntableControlPacket::handle);
        id = toServer(id, LiveStreamerControlPacket.class,
                LiveStreamerControlPacket::encode, LiveStreamerControlPacket::decode,
                LiveStreamerControlPacket::handle);
        id = toServer(id, LyricProjectorConfigPacket.class,
                LyricProjectorConfigPacket::encode, LyricProjectorConfigPacket::decode,
                LyricProjectorConfigPacket::handle);
        id = toServer(id, SpeakerConfigPacket.class,
                SpeakerConfigPacket::encode, SpeakerConfigPacket::decode,
                SpeakerConfigPacket::handle);
        id = toServer(id, VideoProjectorConfigPacket.class,
                VideoProjectorConfigPacket::encode, VideoProjectorConfigPacket::decode,
                VideoProjectorConfigPacket::handle);
        id = toServer(id, HolographicGlassesConfigPacket.class,
                HolographicGlassesConfigPacket::encode, HolographicGlassesConfigPacket::decode,
                HolographicGlassesConfigPacket::handle);
        id = toServer(id, ControlConsoleConfigPacket.class,
                ControlConsoleConfigPacket::encode, ControlConsoleConfigPacket::decode,
                ControlConsoleConfigPacket::handle);
        id = toServer(id, ControlConsoleAccessPacket.class,
                ControlConsoleAccessPacket::encode, ControlConsoleAccessPacket::decode,
                ControlConsoleAccessPacket::handle);
        id = toServer(id, ControlConsoleEditLeasePacket.class,
                ControlConsoleEditLeasePacket::encode, ControlConsoleEditLeasePacket::decode,
                ControlConsoleEditLeasePacket::handle);
        id = toServer(id, ControlConsoleConsumerLeasePacket.class,
                ControlConsoleConsumerLeasePacket::encode, ControlConsoleConsumerLeasePacket::decode,
                ControlConsoleConsumerLeasePacket::handle);
        id = toServer(id, ClearEquippedBindingPacket.class,
                ClearEquippedBindingPacket::encode, ClearEquippedBindingPacket::decode,
                ClearEquippedBindingPacket::handle);
        id = toServer(id, MediaToolConfirmBindingPacket.class,
                MediaToolConfirmBindingPacket::encode, MediaToolConfirmBindingPacket::decode,
                MediaToolConfirmBindingPacket::handle);
        id = toServer(id, MediaToolClearBindingPacket.class,
                MediaToolClearBindingPacket::encode, MediaToolClearBindingPacket::decode,
                MediaToolClearBindingPacket::handle);
        id = toServer(id, MediaToolReportPacket.class,
                MediaToolReportPacket::encode, MediaToolReportPacket::decode,
                MediaToolReportPacket::handle);
        id = toServer(id, WhitelistReviewActionPacket.class,
                WhitelistReviewActionPacket::encode, WhitelistReviewActionPacket::decode,
                WhitelistReviewActionPacket::handle);
        id = toServer(id, MP4StatePacket.class,
                MP4StatePacket::encode, MP4StatePacket::decode, MP4StatePacket::handle);
        id = toServer(id, MP4PlaybackControlPacket.class,
                MP4PlaybackControlPacket::encode, MP4PlaybackControlPacket::decode,
                MP4PlaybackControlPacket::handle);
        id = toServer(id, MP4PlaybackRetryPacket.class,
                MP4PlaybackRetryPacket::encode, MP4PlaybackRetryPacket::decode,
                MP4PlaybackRetryPacket::handle);
        id = toServer(id, MP4EnsureDeviceIdPacket.class,
                MP4EnsureDeviceIdPacket::encode, MP4EnsureDeviceIdPacket::decode,
                MP4EnsureDeviceIdPacket::handle);
        id = toServer(id, MP4EnsureInventoryDeviceIdPacket.class,
                MP4EnsureInventoryDeviceIdPacket::encode, MP4EnsureInventoryDeviceIdPacket::decode,
                MP4EnsureInventoryDeviceIdPacket::handle);
        id = toServer(id, PadStatePacket.class,
                PadStatePacket::encode, PadStatePacket::decode, PadStatePacket::handle);
        id = toServer(id, PadPublishPacket.class,
                PadPublishPacket::encode, PadPublishPacket::decode, PadPublishPacket::handle);
        id = toServer(id, PadPlaybackControlPacket.class,
                PadPlaybackControlPacket::encode, PadPlaybackControlPacket::decode,
                PadPlaybackControlPacket::handle);
        id = toServer(id, PadPlaybackRetryPacket.class,
                PadPlaybackRetryPacket::encode, PadPlaybackRetryPacket::decode,
                PadPlaybackRetryPacket::handle);

        // ─── 服务端 → 客户端 ───
        id = toClient(id, ModernTurntableStopPacket.class,
                ModernTurntableStopPacket::encode, ModernTurntableStopPacket::decode,
                ModernTurntableStopPacket::handle);
        id = toClient(id, AudioEndpointSnapshotPacket.class,
                AudioEndpointSnapshotPacket::encode, AudioEndpointSnapshotPacket::decode,
                AudioEndpointSnapshotPacket::handle);
        id = toClient(id, AreaAudioListenerPacket.class,
                AreaAudioListenerPacket::encode, AreaAudioListenerPacket::decode,
                AreaAudioListenerPacket::handle);
        id = toClient(id, ControlConsoleEditLeaseResultPacket.class,
                ControlConsoleEditLeaseResultPacket::encode, ControlConsoleEditLeaseResultPacket::decode,
                ControlConsoleEditLeaseResultPacket::handle);
        id = toClient(id, ControlConsoleConsumerLeaseResultPacket.class,
                ControlConsoleConsumerLeaseResultPacket::encode, ControlConsoleConsumerLeaseResultPacket::decode,
                ControlConsoleConsumerLeaseResultPacket::handle);
        id = toClient(id, ControlConsoleConfigResultPacket.class,
                ControlConsoleConfigResultPacket::encode, ControlConsoleConfigResultPacket::decode,
                ControlConsoleConfigResultPacket::handle);
        id = toClient(id, MP4DeviceIdPacket.class,
                MP4DeviceIdPacket::encode, MP4DeviceIdPacket::decode, MP4DeviceIdPacket::handle);
        id = toClient(id, MP4InventoryDeviceIdPacket.class,
                MP4InventoryDeviceIdPacket::encode, MP4InventoryDeviceIdPacket::decode,
                MP4InventoryDeviceIdPacket::handle);
        id = toClient(id, MP4ContainerDeviceIdPacket.class,
                MP4ContainerDeviceIdPacket::encode, MP4ContainerDeviceIdPacket::decode,
                MP4ContainerDeviceIdPacket::handle);
        id = toClient(id, MP4DeviceStateMirrorPacket.class,
                MP4DeviceStateMirrorPacket::encode, MP4DeviceStateMirrorPacket::decode,
                MP4DeviceStateMirrorPacket::handle);
        id = toClient(id, MP4PlaybackSyncPacket.class,
                MP4PlaybackSyncPacket::encode, MP4PlaybackSyncPacket::decode,
                MP4PlaybackSyncPacket::handle);
        id = toClient(id, MP4PlaybackTimelinePacket.class,
                MP4PlaybackTimelinePacket::encode, MP4PlaybackTimelinePacket::decode,
                MP4PlaybackTimelinePacket::handle);
        id = toClient(id, PadStateMirrorPacket.class,
                PadStateMirrorPacket::encode, PadStateMirrorPacket::decode, PadStateMirrorPacket::handle);
        id = toClient(id, WhitelistCsvExportPacket.class,
                WhitelistCsvExportPacket::encode, WhitelistCsvExportPacket::decode,
                WhitelistCsvExportPacket::handle);
        id = toClient(id, WhitelistReviewPacket.class,
                WhitelistReviewPacket::encode, WhitelistReviewPacket::decode, WhitelistReviewPacket::handle);
        id = toClient(id, WhitelistReviewMutationResultPacket.class,
                WhitelistReviewMutationResultPacket::encode, WhitelistReviewMutationResultPacket::decode,
                WhitelistReviewMutationResultPacket::handle);
        id = toClient(id, WhitelistPreviewPacket.class,
                WhitelistPreviewPacket::encode, WhitelistPreviewPacket::decode, WhitelistPreviewPacket::handle);
        id = toClient(id, PadMapWorldScopePacket.class,
                PadMapWorldScopePacket::encode, PadMapWorldScopePacket::decode, PadMapWorldScopePacket::handle);
    }

    private static <T> int toServer(int id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add();
        return id + 1;
    }

    private static <T> int toClient(int id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add();
        return id + 1;
    }
}
