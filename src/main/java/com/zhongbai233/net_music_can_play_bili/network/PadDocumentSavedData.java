package com.zhongbai233.net_music_can_play_bili.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import com.zhongbai233.net_music_can_play_bili.item.PadItem;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadDocument;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadMapSettings;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadMediaEntry;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadTriggerMode;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadTriggerPoint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** World-level authoritative Pad documents keyed by stable Pad device id. */
public final class PadDocumentSavedData extends SavedData {
    private static final String NAME = "pad_documents";

    private static final Codec<Map<UUID, PadDocument>> DOCUMENTS_CODEC = Codec
            .unboundedMap(Codec.STRING, DocumentCodec.CODEC)
            .xmap(PadDocumentSavedData::decodeDocuments, PadDocumentSavedData::encodeDocuments);

    public static final Codec<PadDocumentSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DOCUMENTS_CODEC.optionalFieldOf("documents", Map.of()).forGetter(data -> data.documents))
            .apply(instance, PadDocumentSavedData::new));

    // 1.20.1 移植说明：26.1 用 SavedDataType(new ResourceLocation(...), ctor, CODEC, DataFixTypes)
    // 把「标识 + 构造器 + 编解码器 + 数据修复类型」打包注册，序列化由框架经 CODEC 自动完成。
    // 1.20.1 没有 SavedDataType 也没有 SavedData.Factory；改为：
    //   - 获取时用三参数 computeIfAbsent(loader, ctor, name)（见 get 方法）；
    //   - 自行实现 save(CompoundTag) 与静态 loadFromTag(CompoundTag)（见下），
    //     两者经 CODEC + NbtOps 转换，等价于 26.1 由框架代劳的部分。

    private final Map<UUID, PadDocument> documents;

    public PadDocumentSavedData() {
        this(new HashMap<>());
    }

    private PadDocumentSavedData(Map<UUID, PadDocument> documents) {
        this.documents = new HashMap<>(documents);
    }

    public static PadDocumentSavedData get(ServerLevel level) {
        // 1.20.1 的三参数形态：加载器 + 默认构造器 + 存档名。
        return level.getDataStorage().computeIfAbsent(
                PadDocumentSavedData::loadFromTag,
                PadDocumentSavedData::new,
                NAME);
    }

    /**
     * 1.20.1 的序列化入口：{@link SavedData#save(CompoundTag)} 是单参数抽象方法。
     * 26.1 由 {@code SavedDataType} 携带 CODEC 代劳，这里显式实现。
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CODEC.encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(error -> NetMusicCanPlayBili.LOGGER.error(
                        "Failed to save PadDocumentSavedData: {}", error))
                .ifPresent(encoded -> {
                    if (encoded instanceof CompoundTag compound) {
                        tag.merge(compound);
                    }
                });
        return tag;
    }

    /** 与 {@link #save(CompoundTag)} 对称的加载路径。 */
    private static PadDocumentSavedData loadFromTag(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> NetMusicCanPlayBili.LOGGER.warn(
                        "Failed to load PadDocumentSavedData: {}", error))
                .orElseGet(PadDocumentSavedData::new);
    }

    public Optional<PadDocument> document(UUID deviceId) {
        return deviceId == null ? Optional.empty() : Optional.ofNullable(documents.get(deviceId));
    }

    public void put(UUID deviceId, PadDocument document) {
        if (deviceId == null || document == null) {
            return;
        }
        documents.put(deviceId, document.copyWithLocked(false));
        setDirty();
    }

    private static Map<UUID, PadDocument> decodeDocuments(Map<String, DocumentCodec> raw) {
        Map<UUID, PadDocument> decoded = new HashMap<>();
        raw.forEach((key, value) -> {
            try {
                decoded.put(UUID.fromString(key), value.toDocument().copyWithLocked(false));
            } catch (IllegalArgumentException ignored) {
            }
        });
        return decoded;
    }

    private static Map<String, DocumentCodec> encodeDocuments(Map<UUID, PadDocument> documents) {
        Map<String, DocumentCodec> encoded = new HashMap<>();
        documents.forEach((key, value) -> encoded.put(key.toString(), DocumentCodec.from(value)));
        return encoded;
    }

    private record MapSettingsCodec(String dimension, int centerX, int centerZ, int radiusBlocks, float zoom,
            boolean autoFollowPlayer) {
        private static final Codec<MapSettingsCodec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension", PadMapSettings.DEFAULT.dimension())
                .forGetter(value -> value.dimension()),
            Codec.INT.optionalFieldOf("centerX", 0).forGetter(value -> value.centerX()),
            Codec.INT.optionalFieldOf("centerZ", 0).forGetter(value -> value.centerZ()),
            Codec.INT.optionalFieldOf("radiusBlocks", 64).forGetter(value -> value.radiusBlocks()),
            Codec.FLOAT.optionalFieldOf("zoom", 1.0F).forGetter(value -> value.zoom()),
            Codec.BOOL.optionalFieldOf("autoFollowPlayer", true).forGetter(value -> value.autoFollowPlayer()))
            .apply(instance, (dimension, centerX, centerZ, radiusBlocks, zoom, autoFollowPlayer) -> new MapSettingsCodec(
                stringValue(dimension, PadMapSettings.DEFAULT.dimension()),
                intValue(centerX, 0),
                intValue(centerZ, 0),
                intValue(radiusBlocks, 64),
                floatValue(zoom, 1.0F),
                boolValue(autoFollowPlayer, true))));

        private PadMapSettings toSettings() {
            return new PadMapSettings(dimension, centerX, centerZ, radiusBlocks, zoom, autoFollowPlayer);
        }

        private static MapSettingsCodec from(PadMapSettings settings) {
            PadMapSettings safe = settings == null ? PadMapSettings.DEFAULT : settings;
            return new MapSettingsCodec(safe.dimension(), safe.centerX(), safe.centerZ(), safe.radiusBlocks(),
                    safe.zoom(), safe.autoFollowPlayer());
        }
    }

    private record MediaEntryCodec(int mediaId, ItemStack disc) {
        private static final Codec<MediaEntryCodec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("mediaId", 0).forGetter(value -> value.mediaId()),
                // 1.20.1 移植说明：26.1（1.20.5+）用 ItemStack.OPTIONAL_CODEC，允许空栈参与编解码。
            // 1.20.1 只有 ItemStack.CODEC（空栈会被拒绝），但这里外层是
            // optionalFieldOf("disc", ItemStack.EMPTY)：DFU 的 OptionalFieldCodec 在
            // 值等于默认值（即空栈）时会**整个省略该字段**，因此语义与 OPTIONAL_CODEC 一致。
            ItemStack.CODEC.optionalFieldOf("disc", ItemStack.EMPTY).forGetter(value -> value.disc()))
                .apply(instance, (mediaId, disc) -> new MediaEntryCodec(intValue(mediaId, 0), itemStackValue(disc))));

        private PadMediaEntry toEntry() {
            return new PadMediaEntry(mediaId, PadItem.isNetMusicDisc(disc) ? disc : ItemStack.EMPTY);
        }

        private static MediaEntryCodec from(PadMediaEntry entry) {
            return new MediaEntryCodec(entry.mediaId(), entry.disc());
        }
    }

    private record TriggerPointCodec(String pointId, String name, double x, double y, double z, int radiusBlocks,
            int mediaId, String triggerMode, boolean loop, int volumePerMille, boolean visible) {
        private static final Codec<TriggerPointCodec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("pointId", "").forGetter(value -> value.pointId()),
            Codec.STRING.optionalFieldOf("name", "").forGetter(value -> value.name()),
            Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(value -> value.x()),
            Codec.DOUBLE.optionalFieldOf("y", 0.0D).forGetter(value -> value.y()),
            Codec.DOUBLE.optionalFieldOf("z", 0.0D).forGetter(value -> value.z()),
            Codec.INT.optionalFieldOf("radiusBlocks", 8).forGetter(value -> value.radiusBlocks()),
            Codec.INT.optionalFieldOf("mediaId", 0).forGetter(value -> value.mediaId()),
            Codec.STRING.optionalFieldOf("triggerMode", PadTriggerMode.MANUAL.name())
                .forGetter(value -> value.triggerMode()),
            Codec.BOOL.optionalFieldOf("loop", false).forGetter(value -> value.loop()),
            Codec.INT.optionalFieldOf("volumePerMille", 1000).forGetter(value -> value.volumePerMille()),
            Codec.BOOL.optionalFieldOf("visible", true).forGetter(value -> value.visible()))
            .apply(instance,
                (pointId, name, x, y, z, radiusBlocks, mediaId, triggerMode, loop, volumePerMille, visible) -> new TriggerPointCodec(
                    stringValue(pointId, ""),
                    stringValue(name, ""),
                    doubleValue(x, 0.0D),
                    doubleValue(y, 0.0D),
                    doubleValue(z, 0.0D),
                    intValue(radiusBlocks, 8),
                    intValue(mediaId, 0),
                    stringValue(triggerMode, PadTriggerMode.MANUAL.name()),
                    boolValue(loop),
                    intValue(volumePerMille, 1000),
                    boolValue(visible, true))));

        private PadTriggerPoint toPoint() {
            return new PadTriggerPoint(parseUuid(pointId), name, x, y, z, radiusBlocks, mediaId,
                    PadTriggerMode.byName(triggerMode), loop, volumePerMille, visible);
        }

        private static TriggerPointCodec from(PadTriggerPoint point) {
            return new TriggerPointCodec(point.pointId().toString(), point.name(), point.x(), point.y(), point.z(),
                    point.radiusBlocks(), point.mediaId(), point.triggerMode().name(), point.loop(),
                    point.volumePerMille(), point.visible());
        }
    }

    private record DocumentCodec(String title, String author, long updatedAtMillis, long sequence,
            MapSettingsCodec mapSettings, List<MediaEntryCodec> mediaEntries, List<TriggerPointCodec> triggerPoints) {
        private static final Codec<DocumentCodec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("title", "").forGetter(value -> value.title()),
            Codec.STRING.optionalFieldOf("author", "").forGetter(value -> value.author()),
            Codec.LONG.optionalFieldOf("updatedAtMillis", 0L).forGetter(value -> value.updatedAtMillis()),
            Codec.LONG.optionalFieldOf("sequence", 0L).forGetter(value -> value.sequence()),
                MapSettingsCodec.CODEC.optionalFieldOf("mapSettings", MapSettingsCodec.from(PadMapSettings.DEFAULT))
                .forGetter(value -> value.mapSettings()),
                MediaEntryCodec.CODEC.listOf().optionalFieldOf("mediaEntries", List.of())
                .forGetter(value -> value.mediaEntries()),
                TriggerPointCodec.CODEC.listOf().optionalFieldOf("triggerPoints", List.of())
                .forGetter(value -> value.triggerPoints()))
            .apply(instance, (title, author, updatedAtMillis, sequence, mapSettings, mediaEntries, triggerPoints) -> new DocumentCodec(
                stringValue(title, ""),
                stringValue(author, ""),
                longValue(updatedAtMillis, 0L),
                longValue(sequence, 0L),
                mapSettings == null ? MapSettingsCodec.from(PadMapSettings.DEFAULT) : mapSettings,
                mediaEntries == null ? List.of() : List.copyOf(mediaEntries),
                triggerPoints == null ? List.of() : List.copyOf(triggerPoints))));

        private PadDocument toDocument() {
            return new PadDocument(title, author, false, updatedAtMillis, sequence, mapSettings.toSettings(),
                    mediaEntries.stream().map(value -> value.toEntry()).toList(),
                    triggerPoints.stream().map(value -> value.toPoint()).toList());
        }

        private static DocumentCodec from(PadDocument document) {
            PadDocument safe = document == null ? PadDocument.DEFAULT : document;
            return new DocumentCodec(safe.title(), safe.author(), safe.updatedAtMillis(), safe.sequence(),
                    MapSettingsCodec.from(safe.mapSettings()),
                    safe.mediaEntries().stream().map(MediaEntryCodec::from).toList(),
                    safe.triggerPoints().stream().map(TriggerPointCodec::from).toList());
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? UUID.randomUUID() : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }

    private static boolean boolValue(Boolean value) {
        return boolValue(value, false);
    }

    private static boolean boolValue(Boolean value, boolean fallback) {
        return value == null ? fallback : value.booleanValue();
    }

    private static int intValue(Integer value, int fallback) {
        return value == null ? fallback : value.intValue();
    }

    private static long longValue(Long value, long fallback) {
        return value == null ? fallback : value.longValue();
    }

    private static float floatValue(Float value, float fallback) {
        return value == null ? fallback : value.floatValue();
    }

    private static double doubleValue(Double value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }

    private static String stringValue(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static ItemStack itemStackValue(ItemStack value) {
        return value == null ? ItemStack.EMPTY : value;
    }
}