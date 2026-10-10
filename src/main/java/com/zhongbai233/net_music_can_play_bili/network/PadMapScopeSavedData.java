package com.zhongbai233.net_music_can_play_bili.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** 每个服务端存档/后端持久化一个 Pad 地图缓存作用域 UUID。 */
public final class PadMapScopeSavedData extends SavedData {
    private static final String NAME = "pad_map_scope";

    public static final Codec<PadMapScopeSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("worldScopeId").forGetter(data -> data.worldScopeId))
            .apply(instance, PadMapScopeSavedData::new));

    // 1.20.1 移植说明：26.1 用 SavedDataType(new ResourceLocation(...), ctor, CODEC, DataFixTypes)
    // 把「标识 + 构造器 + 编解码器 + 数据修复类型」打包注册，序列化由框架经 CODEC 自动完成。
    // 1.20.1 没有 SavedDataType 也没有 SavedData.Factory；改为：
    //   - 获取时用三参数 computeIfAbsent(loader, ctor, name)（见 get 方法）；
    //   - 自行实现 save(CompoundTag) 与静态 loadFromTag(CompoundTag)（见下），
    //     两者经 CODEC + NbtOps 转换，等价于 26.1 由框架代劳的部分。

    private final String worldScopeId;

    public PadMapScopeSavedData() {
        this(UUID.randomUUID().toString());
        setDirty();
    }

    private PadMapScopeSavedData(String worldScopeId) {
        this.worldScopeId = normalize(worldScopeId);
    }

    public static PadMapScopeSavedData get(ServerLevel level) {
        // 1.20.1 的三参数形态：加载器 + 默认构造器 + 存档名。
        return level.getDataStorage().computeIfAbsent(
                PadMapScopeSavedData::loadFromTag,
                PadMapScopeSavedData::new,
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
                        "Failed to save PadMapScopeSavedData: {}", error))
                .ifPresent(encoded -> {
                    if (encoded instanceof CompoundTag compound) {
                        tag.merge(compound);
                    }
                });
        return tag;
    }

    /** 与 {@link #save(CompoundTag)} 对称的加载路径。 */
    private static PadMapScopeSavedData loadFromTag(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> NetMusicCanPlayBili.LOGGER.warn(
                        "Failed to load PadMapScopeSavedData: {}", error))
                .orElseGet(PadMapScopeSavedData::new);
    }

    public String worldScopeId() {
        return worldScopeId;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }
}
