# NetMusicCanPlayBili (Forge 1.20.1)

[中文](#中文) · [English](#english)

---

## 中文

给 [NetMusic](https://modrinth.com/mod/netmusic) 增加 **BiliBili 视频 / 直播音频**播放支持：把 B 站视频链接（`BV`/`av`）写进音乐 CD 即可播放它的音频；在广播喇叭里输入 `live:直播间ID` 可播放直播音频。

> **本仓库是 [wrdqtww/NetMusicCanPlayBili-1.21.1](https://github.com/wrdqtww/NetMusicCanPlayBili-1.21.1)（NeoForge 1.21.1 分支）到 Forge 1.20.1 的移植版。**
> 上游主线（NeoForge 26.1.x）：<https://github.com/zhongbai2333/NetMusicCanPlayBili>

### 版本矩阵

| 项 | 值 |
|---|---|
| Minecraft | **1.20.1** |
| Forge | **1.20.1-47.4.26**（47.4.x 运行） |
| Java | **17**（字节码目标 17；Gradle 用 JDK 21 运行） |
| Gradle | 8.11（wrapper 自带） |
| mod 版本 | `0.7.10-beta+forge1.20.1` |
| 必需前置 | **NetMusic 1.5.2-forge+mc1.20.1** |
| 可选 | Iris 1.7.0+1.20.1（着色器兼容） |

### 安装

1. 装好 Minecraft 1.20.1 + Forge 47.4.x；
2. 把 **NetMusic**（1.5.2-forge+mc1.20.1）和本仓库 Release 里的 jar 一起放进 `mods/`；
3. 启动游戏。写 B 站链接进音乐 CD、或给广播喇叭 `live:直播间ID`。

### ✅ 当前状态

**完整游玩链路已在实机验证通过（作者实测，全部正常）**：

- ✅ 编译全绿（`compileJava` / `build` 无错误），实机可正常启动与游玩；
- ✅ 方块放置、**CD 刻录 B 站链接**、音频播放、**视频投影仪画面**、**中控台界面**、Pad 地图、
  场景编辑器保存等链路均已实测正常；
- ✅ 音频/视频解码链路（B 站 CDN 优选、原生解码器、AV1→H.264 回落、OpenAL 空间音频、
  播放管道漂移诊断）在实机日志中均正常工作；
- ℹ️ 仍有若干**有意的功能降级**（1.20.1 缺少对应 API 所致），见下文「已知限制」——不影响正常游玩。

---

### 这个 1.20.1 移植做了什么

移植基线取自 **1.21.1 分支的源码**（不是从 26.1 逐行改，因为 26.1 与 1.20.1 的渲染/数据 API 差距过大，
中途自建的兼容层反而成了 bug 温床）。下面是逐类适配内容。

#### 1. 注册表与物品

- `DeferredHolder` / `DeferredItem` / `DeferredBlock` → **`RegistryObject`**；
  `DeferredRegister.Items` / `DeferredRegister.Blocks` → `DeferredRegister<Item>` / `<Block>` + `ForgeRegistries`；
- 1.20.1 没有 `registerSimpleBlockItem(...)`，方块物品需显式注册；
- 装备属性从属性注册表改为物品覆写 **`getDefaultAttributeModifiers(EquipmentSlot)`**；
- 附魔书走 **`ForgeRegistries.ENCHANTMENTS`**（1.20.1 是 `Enchantment` 而非 `Holder<Enchantment>`，已做判空）。

#### 2. 数据组件 → NBT

- `DataComponents` / `CustomData` / `stack.update(...)` → **`getOrCreateTag()` / `getTagElement(...)`**；
- 物品栈序列化 `ItemStack.CODEC` / `OPTIONAL_CODEC` → **`save(...)` / `ItemStack.of(...)`**；
- 方块实体 `loadAdditional` / `saveAdditional(..., HolderLookup.Provider)` → **`load(...)` / `saveAdditional(...)`**，
  另有 0 参 **`saveWithoutMetadata()`**；
- `SavedData.Factory` 构造形式差异一并处理。

#### 3. 方块交互（**一个容易被忽略的死代码坑**）

1.20.5+ 把交互拆成 `useItemOn`（手持物品）+ `useWithoutItem`（空手）；**1.20.1 只有单个 `use(...)`**。
所有方块已合并。其中**有 3 个方块（中控台 / 歌词板 / 扬声器）原本只实现了 `useWithoutItem`**——
它们在 1.20.1 里**不会报错、也不会被调用**，表现为「右键没反应」。这类问题编译期完全看不出来。

#### 4. 渲染

- 顶点 API：`addVertex/setColor/setUv/setNormal` → **`vertex/color/uv/overlayCoords/uv2/normal`**，
  并且**每个顶点都必须补 `endVertex()`**（1.20.1 必需，漏了会静默丢几何）；
- `MeshData` / `ByteBufferBuilder` / `RenderType.draw(MeshData)` 在 1.20.1 **不存在** →
  改用 **`BufferBuilder.RenderedBuffer` / `BufferBuilder` / `setupRenderState() + BufferUploader.drawWithShader() + clearRenderState()`**；
- `VertexBuffer.upload(...)` 会**自己释放** `RenderedBuffer`，二次 `release()` 抛 `IllegalStateException`（已做容错）；
- `RenderStateShard` 的受保护静态字段用 **`port/shim/PortRenderStateShards`** 重新暴露；
- 1.20.1 没有 `mulPose(Matrix4f)`、没有 `MeshData.SortState` / `uploadIndexBuffer`（见「已知限制」）。

#### 5. 网络

- NeoForge 的 `CustomPacketPayload` + `StreamCodec` + `IPayloadContext` →
  Forge **`SimpleChannel`** 的 `encode` / `decode` / `handle(Supplier<NetworkEvent.Context>)` 三件套；
- `PacketDistributor.sendToServer/sendToPlayer/...`（NeoForge 静态便捷方法，1.20.1 不存在）→
  统一封装到 **`NcpbNetworkDispatch`**（约 64 处调用点）；
- 服务端→客户端包改为 `CHANNEL.send(PacketDistributor.PLAYER.with(...), msg)`。

#### 6. 事件与模组入口（**两个实机崩溃点**）

- **模组入口必须是无参构造器**：Forge 1.20.1 的 `FMLModContainer#constructMod` 直接
  `modClass.getDeclaredConstructor()`（零参），**不支持** NeoForge 1.20.5+ 的
  `(IEventBus, ModContainer)` 构造器注入。本移植改为无参 + `FMLJavaModLoadingContext.get().getModEventBus()`；
- **能力注册**：`RegisterCapabilitiesEvent.register(IItemHandler.class)` 会抛
  `Cannot register a capability implementation multiple times`（`IItemHandler` 是 Forge 自带能力，
  已由 Forge 注册）。1.20.1 暴露能力的唯一做法是方块实体覆写 **`getCapability(Capability, Direction)`**；
- `RenderFrameEvent` → `TickEvent.RenderTickEvent(START)`；
  `ClientPauseChangeEvent` → 客户端 tick 比较 `isPaused()`；
  `RegisterMenuScreensEvent` → `MenuScreens.register(...)`；
- `ModConfigSpec` → **`ForgeConfigSpec`**；注册配置用 **`ModLoadingContext.get().registerConfig(...)`**
  （1.20.1 的 `ModContainer` 上没有 `registerConfig`）；
- `DistExecutor` 判端、`@Mod.EventBusSubscriber` 用法按 1.20.1 校正。

#### 7. Java 21 API 清除

`options.release = 17` 下这些全部不可用并已替换：`Math.clamp`、`List#getFirst/getLast`、
`SequencedCollection`、`String#formatted` 等。

#### 8. Mixin / refmap（**本次移植最容易踩的一整类坑**）

1. **refmap 必须真的生成**。只加 `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'` **没有用**——
   该处理器在缺少 `-AoutRefMapFile` 时**一个字节都不写**（`ReferenceManager.write()` 开头即判空返回）。
   还需要 `-AdefaultObfuscationEnv=searge`、`-AmappingTypes=tsrg`、`-AreobfTsrgFile=<tsrg>`，
   并让 `compileJava` 依赖 `createMcpToSrg` 任务；
2. **refmap 文件名必须是模组专属的**（不能叫 `mixin.refmap.json` 这类通用名）。
   Mixin 是按**类路径资源名**读取 refmap 的，整合包里只要有另一个模组用了同名文件，
   谁先被类加载器读到谁生效——本移植实测在装了 `tectonic-forge` 的整合包里因此**启动崩溃**
   （`InvalidAccessorException: No candidates were found matching children:Ljava/util/List;`，
   因为映射整体失效，`@Accessor("children")` 找不到 `f_96540_`）。现已改为
   `net_music_can_play_bili.refmap.json`；
3. **refmap 输出目录要放在 Gradle 保证存在的路径**。放到 `build/tmp/compileJava/` 时
   增量编译能生成、**`clean build` 会静默失败**——表现为「编译成功、jar 里却没有 refmap」。
   本移植改到 `build/generated/sources/annotationProcessor/java/main/`，
   并给 `jar` 任务加了**缺 refmap 即终止构建**的守卫；
3. **`MANIFEST.MF` 的 `MixinConfigs` 属性**是 Forge 发现 mixin 配置的唯一途径。
   缺了它，`mixins.json` 就算在 jar 里也**完全不被读取**，所有 mixin 静默失效、零报错；
4. **生产环境是 SRG 名**（`m_xxxxx_`/`f_xxxxx_`），所以目标是 MC 类的 mixin **不能**写 `remap = false`；
   且 **mixin 类级 `remap` 会被成员继承**——「模组类 mixin 里注入 MC 继承来的方法」必须显式 `remap = true`；
5. **注解处理器不为 `@Shadow` 成员生成 refmap 条目**。访问 MC 私有字段/方法要用
   **`@Accessor` 接口**（本移植已把音频、屏幕等处的 `@Shadow` 全部改写），
   否则实机报 `InvalidMixinException: @Shadow field ... was not located in the target class` 并**崩溃**；
6. **泛型方法上的 `@Invoker` 也拿不到 refmap 条目**（`Screen#addRenderableWidget` 即如此），
   按擦除描述符改写又会被处理器拒绝；最终改为 `@Accessor` 读私有列表 + 自行复刻语义；
7. **mixin 成员名不能撞车**：注入类里的 `@Unique` 成员与 accessor 接口方法同名时，
   Mixin 只警告并**跳过其中一个**（`Method overwrite conflict ... Skipping method`）；
8. 资源层（`mixins.json`、`mods.toml`、模型/语言文件等）**必须入库**——
   曾因一次 `git clean` 把未入库的资源层抹掉，导致模组根本无法加载。

移植附带两个自检脚本，已接入构建流程：

- `work/check_refmap_coverage.py`：逐个 mixin 校验「注入 MC 类的引用是否都在 refmap 里」、
  「是否在 MC 目标上用了 `@Shadow`」、「同目标类注入成员是否重名」；
- `work/find_stub_handlers.py`：扫描网络包，找出「收到包却不处理」的空桩（见下）。

#### 9. 一并修掉的「编译通过但实机不工作」的问题

| 问题 | 症状 | 根因 |
|---|---|---|
| 模组入口构造器 | 启动即崩 `NoSuchMethodException` | Forge 1.20.1 只认无参构造器 |
| 能力重复注册 | 启动崩于 `common_setup` | Forge 自带能力不能再次注册 |
| `@Shadow` 字段 | 启动崩于 mixin APPLY | 该成员没有 refmap 条目 |
| **网络包空桩 handler ×4** | **中控台右键打不开**（服务端已授权、客户端收到结果却丢弃） | 早期移植阶段「客户端类尚未迁移」留下的空桩，依赖后来已迁入但调用未恢复 |
| 方块交互死代码 | 3 个方块右键无反应 | `useWithoutItem` 在 1.20.1 不会被调用 |
| refmap 未打进 jar | 实机所有 MC 目标 mixin 失效 | clean 构建下处理器静默失败 |
| `mixins.json` 条目不全 | 15+ 个 mixin 静默失效 | 沿用了旧基线只列 4 个的版本 |

#### 10. 已知限制

- **地形预览的半透明索引重排**：1.21 的「相机移动只重排索引」依赖 `MeshData.SortState` 与
  `VertexBuffer#uploadIndexBuffer`，1.20.1 **无 API 对应物**，已改为不重排（**有意的降级**，可能影响排序观感）；
- **投影仪裁剪盒的真实宽高比**：真实画面比例来自客户端解码会话（仅在客户端可得），
  方块实体侧只能用 16:9 默认值，极端宽幅画面仍可能被提前裁剪（助手类已预留 `aspect` 重载入口）；
- **附魔光效**：1.20.1 无 `enchantment_glint_override`，绑定光效未复现；
- **1.21.1 存档不兼容**：物品栈从「数据组件」变为 NBT 布局，旧存档里的队列条目会解析失败（静默变空，不崩）；
- **中控台配置结果入口**：真正的分发在包私有基类 `HolographicEditorLifecycleScreen` 中，
  1.20.1 侧在 public 的 `HolographicScreenConfigTestScreen` 上新增了一个 public 转发方法（与 1.21.1 的调用形态一致）。

### 构建

```bash
./gradlew build
# 产物：build/libs/net_music_can_play_bili-0.7.10-beta+forge1.20.1.jar
```

- 需要 **JDK 21 运行 Gradle**、**JDK 17 作为 toolchain**（字节码目标锁定 17）；
- 首次构建需能访问 `maven.minecraftforge.net`、`api.modrinth.com/maven`、`services.gradle.org`；
- **本机专属设置请写在自己的 `<GRADLE_USER_HOME>/gradle.properties`**（例如 `org.gradle.java.home`），
  **不要提交进仓库**——本仓库的 `gradle.properties` 刻意只保留可移植内容。

### 许可与致谢

**MIT**，见 [LICENSE](LICENSE)。

- 原模组与上游主线：© [zhongbai233](https://github.com/zhongbai2333/NetMusicCanPlayBili)
- 1.21.1 (NeoForge) 分支：© [wrdqtww](https://github.com/wrdqtww/NetMusicCanPlayBili-1.21.1)
- Forge 1.20.1 移植：© [ChihayaAnonQWQ](https://github.com/ChihayaAnonQWQ)

**非官方移植**，与原作者及 1.21.1 分支作者无隶属关系；问题请提到本仓库 Issues。
依赖的 B 站接口由第三方提供，请遵守 B 站服务条款；本项目仅供学习交流。

---

## English

Adds **BiliBili video / live-stream audio** playback to [NetMusic](https://modrinth.com/mod/netmusic):
write a Bilibili link (`BV`/`av`) into a music CD and its audio plays; type `live:<roomId>` into a
loudspeaker to play a live stream.

> **This repository is a port of [wrdqtww/NetMusicCanPlayBili-1.21.1](https://github.com/wrdqtww/NetMusicCanPlayBili-1.21.1)
> (the NeoForge 1.21.1 branch) to Forge 1.20.1.**
> Upstream main line (NeoForge 26.1.x): <https://github.com/zhongbai2333/NetMusicCanPlayBili>

### Version matrix

| Item | Value |
|---|---|
| Minecraft | **1.20.1** |
| Forge | **1.20.1-47.4.26** (runs on 47.4.x) |
| Java | **17** (bytecode target 17; Gradle runs on JDK 21) |
| Gradle | 8.11 (wrapper included) |
| Mod version | `0.7.10-beta+forge1.20.1` |
| Required dependency | **NetMusic 1.5.2-forge+mc1.20.1** |
| Optional | Iris 1.7.0+1.20.1 (shader compatibility) |

### Installation

1. Install Minecraft 1.20.1 + Forge 47.4.x;
2. Drop both **NetMusic** (1.5.2-forge+mc1.20.1) and the jar from this repo's Releases into `mods/`;
3. Launch. Write a Bilibili link into a music CD, or use `live:<roomId>` on a loudspeaker.

### ✅ Current status

**The full gameplay loop has been verified in game (tested by the author — everything works)**:

- ✅ Compiles clean (`compileJava` / `build` with no errors); launches and plays normally;
- ✅ Block placement, **burning a Bilibili link onto a CD**, audio playback, **projector video**,
  the **control console GUI**, the Pad map, scene-editor saving and more have all been verified in game;
- ✅ The audio/video decoding chain (Bilibili CDN selection, native decoder, AV1→H.264 fallback,
  OpenAL spatial audio, playback drift diagnostics) works as shown in the logs;
- ℹ️ A few **intentional feature degradations** remain because 1.20.1 lacks the corresponding APIs —
  see "Known limitations" below. They do not affect normal gameplay.

---

### What this 1.20.1 port actually changed

The starting point was the **1.21.1 branch source** (not a line-by-line edit of the 26.1 code — the
26.1↔1.20.1 render/data API gap was large enough that the hand-written compatibility layer became the
main source of bugs). The adaptation, by category:

#### 1. Registries and items

- `DeferredHolder` / `DeferredItem` / `DeferredBlock` → **`RegistryObject`**;
  `DeferredRegister.Items` / `DeferredRegister.Blocks` → `DeferredRegister<Item>` / `<Block>` + `ForgeRegistries`;
- 1.20.1 has no `registerSimpleBlockItem(...)` — block items must be registered explicitly;
- Equipment attributes moved from the attribute registry to an item override of
  **`getDefaultAttributeModifiers(EquipmentSlot)`**;
- Enchanted books go through **`ForgeRegistries.ENCHANTMENTS`** (1.20.1 uses `Enchantment`, not
  `Holder<Enchantment>`; null-guarded).

#### 2. Data components → NBT

- `DataComponents` / `CustomData` / `stack.update(...)` → **`getOrCreateTag()` / `getTagElement(...)`**;
- Item stack serialization `ItemStack.CODEC` / `OPTIONAL_CODEC` → **`save(...)` / `ItemStack.of(...)`**;
- Block entities: `loadAdditional` / `saveAdditional(..., HolderLookup.Provider)` → **`load(...)` /
  `saveAdditional(...)`**, plus the 0-arg **`saveWithoutMetadata()`**;
- `SavedData.Factory` construction differences handled.

#### 3. Block interaction (**an easy-to-miss dead-code trap**)

1.20.5+ splits interaction into `useItemOn` (with an item) + `useWithoutItem` (empty hand);
**1.20.1 only has a single `use(...)`**. All blocks were merged. Notably, **three blocks
(control console / lyric projector / speaker) only implemented `useWithoutItem`** — on 1.20.1 those
methods **never run and never error**, so right-clicking did nothing. A compiler cannot catch this.

#### 4. Rendering

- Vertex API: `addVertex/setColor/setUv/setNormal` → **`vertex/color/uv/overlayCoords/uv2/normal`**,
  and **every vertex needs an explicit `endVertex()`** on 1.20.1 (omitting it silently drops geometry);
- `MeshData` / `ByteBufferBuilder` / `RenderType.draw(MeshData)` **do not exist** on 1.20.1 →
  **`BufferBuilder.RenderedBuffer` / `BufferBuilder` /
  `setupRenderState() + BufferUploader.drawWithShader() + clearRenderState()`**;
- `VertexBuffer.upload(...)` **releases** the `RenderedBuffer` itself; a second `release()` throws
  `IllegalStateException` (tolerated);
- Protected `RenderStateShard` statics re-exposed via **`port/shim/PortRenderStateShards`**;
- 1.20.1 lacks `mulPose(Matrix4f)`, `MeshData.SortState` and `uploadIndexBuffer` (see limitations).

#### 5. Networking

- NeoForge `CustomPacketPayload` + `StreamCodec` + `IPayloadContext` → Forge **`SimpleChannel`**'s
  `encode` / `decode` / `handle(Supplier<NetworkEvent.Context>)` trio;
- `PacketDistributor.sendToServer/sendToPlayer/...` (NeoForge static helpers, absent on 1.20.1) →
  centralized in **`NcpbNetworkDispatch`** (~64 call sites);
- Server→client packets now go through `CHANNEL.send(PacketDistributor.PLAYER.with(...), msg)`.

#### 6. Events and the mod entry point (**two real crash sites**)

- **The mod class must have a no-arg constructor**: Forge 1.20.1's `FMLModContainer#constructMod`
  calls `modClass.getDeclaredConstructor()` (zero args) and does **not** support the NeoForge 1.20.5+
  `(IEventBus, ModContainer)` constructor injection. Changed to a no-arg constructor plus
  `FMLJavaModLoadingContext.get().getModEventBus()`;
- **Capability registration**: `RegisterCapabilitiesEvent.register(IItemHandler.class)` throws
  `Cannot register a capability implementation multiple times` (`IItemHandler` is a built-in Forge
  capability that Forge already registers). On 1.20.1 the only way to expose a capability is a
  block-entity override of **`getCapability(Capability, Direction)`**;
- `RenderFrameEvent` → `TickEvent.RenderTickEvent(START)`; `ClientPauseChangeEvent` → compare
  `isPaused()` on client tick; `RegisterMenuScreensEvent` → `MenuScreens.register(...)`;
- `ModConfigSpec` → **`ForgeConfigSpec`**; config registration via
  **`ModLoadingContext.get().registerConfig(...)`** (1.20.1's `ModContainer` has no `registerConfig`);
- `DistExecutor` side checks and `@Mod.EventBusSubscriber` usage corrected for 1.20.1.

#### 7. Java 21 API removal

Under `options.release = 17` these are unavailable and were replaced: `Math.clamp`,
`List#getFirst/getLast`, `SequencedCollection`, `String#formatted`, and others.

#### 8. Mixin / refmap (**the single biggest class of traps in this port**)

1. **The refmap must actually be generated.** Adding
   `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'` **is not enough** — the processor
   writes **not a single byte** unless `-AoutRefMapFile` is present (its `ReferenceManager.write()`
   returns immediately). You also need `-AdefaultObfuscationEnv=searge`, `-AmappingTypes=tsrg`,
   `-AreobfTsrgFile=<tsrg>`, and `compileJava` must depend on the `createMcpToSrg` task;
2. **The refmap file name must be mod-specific** (never a generic name such as `mixin.refmap.json`).
   Mixin loads refmaps **by classpath resource name**, so if any other mod in a pack ships a file with the
   same name, whichever the classloader finds first wins — in this port that caused a **startup crash** in a
   pack containing `tectonic-forge` (`InvalidAccessorException: No candidates were found matching
   children:Ljava/util/List;`, because the whole mapping was lost and `@Accessor("children")` could not find
   `f_96540_`). It is now named `net_music_can_play_bili.refmap.json`;
3. **The refmap output directory must be one Gradle guarantees to exist.** Writing to
   `build/tmp/compileJava/` works for incremental builds but **silently fails on `clean build`** —
   producing a successful build with **no refmap in the jar**. This port writes to
   `build/generated/sources/annotationProcessor/java/main/` and added a `jar`-task guard that
   **fails the build** when no refmap exists;
3. **The `MixinConfigs` attribute in `MANIFEST.MF`** is the only way Forge discovers a mixin config.
   Without it, `mixins.json` is never read even when it is inside the jar — every mixin silently dies;
4. **Production runs on SRG names** (`m_xxxxx_`/`f_xxxxx_`), so mixins targeting Minecraft classes must
   **not** set `remap = false`; also **a class-level `remap` is inherited by members**, so a mod-class
   mixin that injects an inherited Minecraft method needs an explicit `remap = true`;
5. **The annotation processor emits no refmap entries for `@Shadow` members.** Accessing private
   Minecraft fields/methods requires an **`@Accessor` interface** (this port converted all such
   `@Shadow` usages); otherwise the game dies with
   `InvalidMixinException: @Shadow field ... was not located in the target class`;
6. **`@Invoker` on generic methods also gets no refmap entry** (`Screen#addRenderableWidget` is one
   example), and rewriting it with the erased descriptor is rejected by the processor. Solved with an
   `@Accessor` on the private list plus a faithful re-implementation of the semantics;
7. **Mixin member names must not collide**: a `@Unique` member in an injector class with the same name
   as an accessor interface method makes Mixin warn and **skip one of them**
   (`Method overwrite conflict ... Skipping method`);
8. **The resource layer (`mixins.json`, `mods.toml`, models, lang files) must be committed** — a
   `git clean` once wiped the uncommitted resource layer and the mod could not load at all.

Two self-check scripts ship with the port and are wired into the build flow:

- `work/check_refmap_coverage.py`: per-mixin verification that every Minecraft-targeting reference is in
  the refmap, that no `@Shadow` is used on a Minecraft target, and that injected member names do not collide;
- `work/find_stub_handlers.py`: scans the network package for handlers that receive a packet and do
  nothing (see below).

#### 9. "Compiles fine, broken in game" issues fixed along the way

| Issue | Symptom | Root cause |
|---|---|---|
| Mod entry constructor | Crash on startup: `NoSuchMethodException` | Forge 1.20.1 only accepts a no-arg constructor |
| Duplicate capability registration | Crash during `common_setup` | Built-in Forge capabilities cannot be re-registered |
| `@Shadow` field | Crash during mixin APPLY | No refmap entry is generated for it |
| **4 no-op packet handlers** | **Control console GUI would not open** (server granted the lease, the client dropped the reply) | Empty stubs left by an earlier porting stage, whose dependencies have since been ported |
| Block interaction dead code | 3 blocks ignored right-click | `useWithoutItem` is never invoked on 1.20.1 |
| Refmap missing from the jar | Every Minecraft-targeting mixin failed in game | The processor silently failed during `clean build` |
| Incomplete `mixins.json` | 15+ mixins silently disabled | The old-baseline config listed only 4 |

#### 10. Known limitations

- **Translucent index re-sorting for the terrain preview**: the 1.21 "re-sort indices only when the
  camera moves" path relies on `MeshData.SortState` and `VertexBuffer#uploadIndexBuffer`, which have
  **no 1.20.1 equivalent**. Left unsorted — an **intentional degradation** that may affect ordering;
- **Real aspect ratio in the projector culling box**: the true frame aspect comes from the client-side
  decode session; the block entity can only assume 16:9, so extremely wide content may still be culled
  early (an `aspect` overload is already provided on the helper);
- **Enchantment glint**: 1.20.1 has no `enchantment_glint_override`; the bound-item glint is not reproduced;
- **1.21.1 saves are not compatible**: item stacks moved from data components to NBT, so queue entries
  in old saves fail to parse (they silently become empty; no crash);
- **Control console config-result entry point**: the real dispatcher lives in the package-private
  `HolographicEditorLifecycleScreen`; the 1.20.1 side adds a public forwarding method on the public
  `HolographicScreenConfigTestScreen` (mirroring how the 1.21.1 branch calls it).

### Building

```bash
./gradlew build
# output: build/libs/net_music_can_play_bili-0.7.10-beta+forge1.20.1.jar
```

- Requires **JDK 21 to run Gradle** and a **JDK 17 toolchain** (bytecode target is pinned to 17);
- The first build needs access to `maven.minecraftforge.net`, `api.modrinth.com/maven` and
  `services.gradle.org`;
- **Keep machine-specific settings in your own `<GRADLE_USER_HOME>/gradle.properties`** (such as
  `org.gradle.java.home`) — **do not commit them**. This repo's `gradle.properties` deliberately
  contains portable settings only.

### License and credits

**MIT**, see [LICENSE](LICENSE).

- Original mod and upstream main line: © [zhongbai233](https://github.com/zhongbai2333/NetMusicCanPlayBili)
- 1.21.1 (NeoForge) branch: © [wrdqtww](https://github.com/wrdqtww/NetMusicCanPlayBili-1.21.1)
- Forge 1.20.1 port: © [ChihayaAnonQWQ](https://github.com/ChihayaAnonQWQ)

**Unofficial port** — not affiliated with the original author or the 1.21.1 branch author. Please file
issues in this repository. It relies on third-party Bilibili endpoints; respect Bilibili's terms of
service. Provided for learning and interoperability purposes.
