# Spark-Core 世界生成数据模块实施计划

> **状态**: 基础注入与内置结构模板 GameTest 已通过；动态 worldgen 注册表等服务端验收待完成 · **创建**: 2026-11-27 · **修订**: v4
>
> **目标版本**: Minecraft 1.21.1 / NeoForge 21.1.219 / Spark-Core 1.x
>
> **关联**: Machine-Max `docs/plan/计划中/载具系统-结构生成载具备忘.md`。消费方的 StructureProcessor、区块待办与载具兑现不在本文范围。

## 1. 目标与范围

为 Spark 内容包新增四个直接对应 Minecraft 数据包目录的模块：`worldgen`、`structure`、`tags`、`loot_table`。内容包作者可以把 `data/<namespace>/` 下对应目录的内容放到 Spark 包同名模块中。Spark 在服务器创建世界、读取数据包注册表前，将这些内容作为虚拟 `SERVER_DATA` 数据包提供给 Minecraft。

首阶段只保证启动/创建世界时应用内容包数据。不承诺在世界运行期间通过 `/reload` 或 Spark 内容包重载更新动态 worldgen 注册表；不新增通用 `data` 模块，也不把 `worldgen` 下每一种注册表拆成独立 Spark 模块。

| 纳入 | 不纳入 |
|------|--------|
| 四个目录根的路径映射与服务端数据包注入 | 在世 worldgen 注册表重载 |
| JSON、NBT 原样传递 | Spark 自行解析或改写 JSON/NBT |
| 进入世界时的动态注册表解析验收 | Machine-Max 的车辆 processor、chunk attachment 与实例化逻辑 |
| 按消费者契约选择读取/同步模式 | 任意 Minecraft 数据包顶层目录的通用导入器 |

## 2. 现状

- `ReadablePathType` 按 `namespace/module/path` 拆分包路径；模块名是 namespace 之后的第一段。见 `src/main/kotlin/cn/solarmoon/spark_core/pack/readable/ReadablePathType.kt`。
- 模块由 `SparkPackModuleRegister` 注册，并通过 `SparkPackModule.read(...)` 收到文件字节和模块内相对路径。见 `src/main/kotlin/cn/solarmoon/spark_core/registry/common/SparkPackModuleRegister.kt` 与 `src/main/kotlin/cn/solarmoon/spark_core/pack/modules/SparkPackModule.kt`。
- `SparkVirtualResourcePack` 以 `PackType -> ResourceLocation -> ByteArray` 保存资源，适用于 JSON 与二进制 NBT。见 `src/main/kotlin/cn/solarmoon/spark_core/pack/SparkVirtualResourcePack.kt`。
- `RecipeModule` 已将内容写入 `DATA_PACK`，但会解析并重新序列化 JSON；新模块必须保留输入字节。见 `src/main/kotlin/cn/solarmoon/spark_core/pack/modules/RecipeModule.kt`。
- `ReadMode` 同时影响本地内容包扫描和 Spark 自定义 payload 是否携带模块原始文件；`collectRemote()` 按模块的 `shouldSend()` 过滤。消费者审计确认 Machine-Max 客户端订阅 `RecipesUpdatedEvent` 并从事件提供的 `RecipeManager` 重建索引；这是消费原生同步后 recipe 的下游路径，不等同于 Spark 的 recipe reader。因此本实现不改变既有 recipe 模块模式或消费契约。
- 对当前可访问的 Spark-Core、Machine-Max、ARMS-Core 与 BallisticsFramework 源码进行了新增根消费者审计：没有发现客户端读取 `worldgen`、`structure`、`tags` 或 `loot_table` Spark 原始文件的消费者。Machine-Max 的结构生成计划由服务端 worldgen processor 消费；其客户端物品浏览读取已解析的 `ItemStack` tag，而非原始 tag JSON。因此四个新模块使用 `SERVER_LOCAL_ONLY`，Minecraft 解析后可同步的运行时数据仍由 Minecraft/NeoForge 自己同步。该结论限于当前工作区消费者，不代表第三方未来消费者可忽略 Spark 原始 payload 契约。
- Minecraft 原生同步与 Spark payload 是两条独立通道。目标版本 1.21.1 会同步已解析的配方；标签仅对网络安全注册表同步；本机 NeoForge 21.1.219 的 `RegistryDataLoader.SYNCHRONIZED_REGISTRIES` 包含 biome、dimension type 等，但不包含 structure、structure set、processor list、template pool。原生同步的是其支持的运行时数据，不是 Spark 模块原始文件，也不能替代下游对原始内容的消费。
- `SparkPackLoaderApplier` 在 `AddPackFindersEvent` 的 `SERVER_DATA` 分支读取服务端内容并注册虚拟数据包。worldgen 数据必须在 Minecraft 加载动态注册表前进入 pack；`AddReloadListenerEvent` 不作为首轮注入入口。
- 本地 NeoForge 21.1.219 sources 中，`StructureTemplateManager` 使用 `structure` 根目录与 `.nbt` 后缀读取模板，因此模块 ID 是单数 `structure`。

## 3. 内容包目录契约

内容包中的 namespace 目录对应数据包 namespace；不需要也不应额外添加 `data/` 前缀。

| Spark 内容包路径 | 虚拟数据包路径 | 内容 |
|------------------|----------------|------|
| `<pack>/<namespace>/worldgen/<相对路径>.json` | `data/<namespace>/worldgen/<相对路径>.json` | 保留整个 worldgen 根，包括 `structure`、`structure_set`、`template_pool`、`processor_list` 等子目录 |
| `<pack>/<namespace>/structure/<相对路径>.nbt` | `data/<namespace>/structure/<相对路径>.nbt` | 压缩结构模板 NBT，保留嵌套路径 |
| `<pack>/<namespace>/tags/<相对路径>.json` | `data/<namespace>/tags/<相对路径>.json` | 各类 tag，例如 `tags/worldgen/structure`、`tags/worldgen/biome` |
| `<pack>/<namespace>/loot_table/<相对路径>.json` | `data/<namespace>/loot_table/<相对路径>.json` | 战利品表；使用 1.21.1 单数目录名 `loot_table` |

典型结构生成依赖链：

```text
worldgen/structure_set -> worldgen/structure
  -> jigsaw template_pool -> structure/*.nbt
                       \-> worldgen/processor_list -> 目标模组代码注册的 StructureProcessorType
```

`tags` 与 `loot_table` 是 `worldgen`、`structure` 之外的同级数据包根，不能由 `worldgen` 模块代替。模块原始文件由谁读取、是否通过 Spark payload 发送，必须按消费者契约决定；Minecraft 原生数据包同步只提供其协议明确覆盖的解析后数据，不能据此推断 Spark 原始内容对客户端或下游消费者无用。

## 4. 模块设计

### 4.1 路径与字节处理

每个模块将模块 ID 作为 Minecraft 数据包资源路径的固定根。以 `worldgen` 为例，模块收到的 `pathSegments` 依次是 `structure`、`ruins`，文件名为 `outpost.json`，最终资源 ID 为：

```text
<namespace>:worldgen/structure/ruins/outpost.json
```

可新增共用的 `ServerDataRootModule`，由四个模块实例化并分别使用四个模块 ID；也可使用四个轻量模块类。不得把原目录层级折叠到通用 `data` 模块。

- 用 `ResourceLocation.fromNamespaceAndPath` 构造资源 ID；非法路径应进入现有内容包诊断。
- `worldgen`、`tags`、`loot_table` 只接收 `.json`，`structure` 只接收 `.nbt`；不支持的后缀应有清晰诊断。
- 有效文件以原始 `ByteArray` 写入 `DATA_PACK`。不要通过 `PackCodec`、Gson、SNBT 或压缩/解压再序列化中转。
- Spark 不验证 Minecraft Codec/schema；语法、动态注册表 ID 和跨文件引用由 Minecraft 加载阶段验证，Spark 负责保留文件来源与路径诊断。
- 同一 `ResourceLocation` 的覆盖沿用内容包图解析顺序和虚拟 pack 的 `put` 行为；实现时确认依赖包后加载覆盖前序包，并添加回归测试。若顺序不稳定，先解决确定性与诊断。

### 4.2 读取模式

新增 `SERVER_LOCAL_ONLY`，其 server/client 读取矩阵如下。此模式会阻断客户端及下游模块取得 Spark 原始内容，因而必须先完成消费者审计；本计划四个模块的审计结论见 §2：

| 调用侧 | `shouldRead` | `shouldSend` |
|--------|--------------|--------------|
| 服务端 | `true` | `false` |
| 客户端 | `false` | `false` |

`SERVER_LOCAL_ONLY` 仅适用于已确认没有客户端侧原始内容消费者的模块。不得因为 Spark-Core 内置 reader 不读取某模块，就推断外部模块、MM 或其他订阅者不需要它；存在客户端原始内容消费者时，应保留 `SERVER_TO_CLIENT` 或明确设计单独的同步策略。若将来有新的客户端原始内容消费者，必须重新评估对应模块的模式或设计单独同步协议。

该模式只控制 Spark 本地扫描和 Spark 自定义 payload，不拦截 Minecraft/NeoForge 的配方、标签或注册表同步。反过来，原生同步也不能替代 Spark 原始内容传输：它只发送协议覆盖的数据。例如配方会由 1.21.1 原生同步；tags 和动态注册表仅在对应注册表可网络同步时覆盖；structure、structure set、processor list、template pool 与 structure NBT 不在内置同步清单/流程中。客户端若通过 Spark 下游模块直接消费这些原始文件，不能使用该模式。

### 4.3 初次世界数据包装配

复用 `SparkPackLoaderApplier` 现有 `SERVER_DATA` 流程：

1. `AddPackFindersEvent` 收到 `PackType.SERVER_DATA` 时，按现有加载会话读取 Spark 内容包。
2. 四个模块将路径映射后的字节写入 `DATA_PACK`。
3. 现有 `ServerPacksSource.fixedResources(DATA_PACK)` 将虚拟资源包加入服务器数据包仓库。
4. Minecraft 按正常数据包流程解析动态注册表和资源引用。

不要把 worldgen JSON 延后到 `AddReloadListenerEvent` 才注入；该 listener 不是首轮动态注册表装载前置点。当前实现会在每次创建 `SERVER_DATA` pack repository 时先清空虚拟包中的服务端资源，再从当前内容包重新装配，避免旧世界条目或已有 recipe 令后续加载被跳过。

本计划不提供在世重载保证。Spark 内容包变化后，需退出并重新启动游戏/服务器，再创建或进入世界以重新构造首次数据包；不得声称 `/reload` 可更新 worldgen 注册表。未来若支持动态重载，应另立计划处理资源仓库重建、注册表重载、缓存失效和失败回滚。

## 5. Machine-Max 集成边界

Spark 只提供数据包资源。Machine-Max 后续负责：

- 在模组注册阶段注册 `machine_max:spawn_vehicle` processor 类型与 Codec。
- 让 `worldgen/processor_list` JSON 能引用该类型。
- processor 阶段只记录不可变生成请求；不在 worldgen 回调中创建物理体或直接加入 `ObjectManager`。
- 目标区块完整加载后校验并兑现请求，处理持久化、重试和幂等。

该拆分与 Machine-Max 当前结构生成备忘一致。Spark 不依赖 Machine-Max，不认识车辆模板 ID，也不需要车辆专属模块。

## 6. 分阶段实施

### P0：固定作者格式与覆盖规则

- 固定模块 ID 为 `worldgen`、`structure`、`tags`、`loot_table`，与 Minecraft 数据包目录一致。
- 输入不含 `data/` 前缀；namespace 是 Spark 包路径第一段。
- 明确扩展名错误、非法资源路径和同路径多包冲突的诊断与覆盖语义。
- [x] 审计当前工作区 Spark-Core、Machine-Max、ARMS-Core 与 BallisticsFramework 消费者，区分 Spark 原始内容与 Minecraft 原生同步后的运行时数据；结论见 §2。新发现的客户端原始内容消费者须重新审查策略。

### P1：实现服务端数据模块

- [x] 增加 `SERVER_LOCAL_ONLY` 读取模式；仅为审计确认没有客户端原始内容消费者的四个新增模块启用，不改动既有 recipe 模块策略。
- [x] 新增四个根目录直通模块，并在 `SparkPackModuleRegister` 注册。
- [x] 统一路径构造、保留子目录、不解析或重写有效文件字节。
- [x] 统计模块成功写入数量；文件级异常沿用 `SparkPackLoader` 的 `PackIssue` 通道。

### P2：接入初次 `SERVER_DATA` 数据包

- [x] 四模块在 `AddPackFindersEvent` 的首次服务器数据包读取中写入，并在 `ServerPacksSource.fixedResources(DATA_PACK)` 注册前完成。
- [x] 每次创建 `SERVER_DATA` pack repository 时先清空 Spark 虚拟数据包的服务端资源，再从当前内容包图完整装配；不以既有资源数量跳过加载。
- [x] 保持既有 pack repository 选择策略；四模块不新增 reload listener。

### P3：自动化测试

> 当前验证（2026-11-27）：`ServerDataRootModuleTest` 6 项定向用例通过；`runGameTestServer` 共 11 项通过，其中 `SparkWorldgenPackGameTest` 从内置 Spark 包读取 `spark_core:worldgen_probe`，实际放置并逐格确认 9 个铁块和信标。全量 `test` 执行 212 项、1 项失败，失败为既有 `DoublePrecisionNativeTest` 期望 native `23.1.0`、实际加载 `23.1.2`。worldgen 动态注册表 JSON 解码及 tags/loot table 生命周期仍待单独 fixture 验收。

**单元测试**：

- [x] 四模块将文件映射到预期 namespace/path；`worldgen` 的多层目录保持完整。
- `processor_list`、`template_pool`、`structure_set` 均是 `worldgen` 下的普通子路径，不是独立 Spark 模块。
- [x] NBT 输入输出字节完全一致，fixture 覆盖 `0x00` 和非 UTF-8 字节；worldgen JSON 输入输出字节完全一致。
- [x] `SERVER_LOCAL_ONLY` 的 server/client 读取矩阵及 `shouldSend` 符合 §4.2；该模式只控制 Spark 内容包读取/payload，不改变 Minecraft 原生数据同步。
- [x] 每个新模块的消费者审计结论见 §2；定向测试覆盖 `shouldSend` 决策。完整 payload 过滤回归和第三方消费者策略仍待补测。
- [ ] 同路径资源的依赖顺序覆盖行为有回归测试。
- [ ] 无效扩展名、非法资源路径经现有内容包诊断报告具体文件位置。

**服务端集成测试**：

- [ ] 准备最小 vanilla fixture，覆盖 structure、structure set、template pool、processor list 与 tag。
- [ ] 创建测试世界后，从真实服务器 `RegistryAccess` 查询 fixture 注册表 ID，确认 JSON 在动态注册表加载前可见且成功解码。
- [x] 通过 `StructureTemplateManager` 查询并放置内置模板：`spark_modules/spark_core.default/spark_core/structure/worldgen_probe.nbt` → `spark_core:worldgen_probe`；GameTest 断言 3×2×3 模板中的 9 个铁块与信标。
- [ ] 提供一个使用战利品表的结构内容 fixture，确认 loot table 经同一虚拟 `SERVER_DATA` pack 加载。
- [ ] 覆盖无 Spark 资源、只有 recipe、四类资源混合三种启动情况；额外验证重复创建 server pack repository 时旧资源会清除、新内容会重新装配。

### P4：消费方验收

- Machine-Max 单独注册测试 processor 类型，并由数据包 processor list 引用，证明 Spark 不需了解自定义 Codec。
- 在真实 worldgen 路径验证 processor 得到调用并写入请求；载具兑现仍按 Machine-Max 的 chunk attachment/GameTest 计划验收。
- Spark-Core 只验收“资源进入动态注册表/模板管理器”；车辆生成、跨区块、重启恢复与幂等不归 Spark 测试负责。

## 7. 兼容性、风险与缓解

| 风险 | 缓解 |
|------|------|
| 在世 reload 时 JSON 晚于动态注册表加载才进入虚拟 pack | 明确无 reload 保证，只在首次数据包仓库构造时注入；未来另立计划 |
| 进程内再次创建世界时旧资源残留或导致新一轮加载被跳过 | 每次创建 server pack repository 都清空 `SERVER_DATA` 并完整重读当前内容包；覆盖重复创建测试 |
| JSON 经 Spark 重序列化后改变 vanilla 可接受语法 | 原始字节直通，不调用 `RecipeModule` 的 JSON 清理逻辑 |
| 结构 NBT 被文本解码、损坏或路径误用复数 | `structure` 专用原始字节路径，按 `.nbt` 验收并对照 1.21.1 `StructureTemplateManager` |
| 引用的 tag、loot table 或 processor 类型缺失 | 提供四个约定根；Minecraft 负责 Codec/引用验证，Spark 保留原包文件定位 |
| 多包同资源 ID 结果受加载次序影响 | 以依赖图顺序定义覆盖并测试；必要时增加冲突诊断 |
| `SERVER_LOCAL_ONLY` 意外切断 MM 或其他下游的客户端原始内容消费者 | 已审计当前工作区并按模块记录策略；新增客户端原始内容消费者时必须重新评估模式或单独同步 |
| 将 Minecraft 原生同步误当成 Spark 原始内容同步的替代 | 两条通道分别验收；只依赖已确认网络可同步的运行时数据，否则保留 Spark 同步或另设消费方协议 |

## 8. 验收清单

- [x] 作者可将数据包 `data/<namespace>/worldgen`、`structure`、`tags`、`loot_table` 的目录内容分别放入 Spark 同名模块，无需转换器。
- [x] 资源映射到正确的 `data/<namespace>/...` 路径，嵌套相对路径不变。
- [x] JSON 与 NBT 字节原样写入虚拟 `SERVER_DATA` pack。
- [ ] 创建/进入世界时，worldgen 动态注册表、标签和战利品表均由 Minecraft 正常加载。
- [x] Minecraft 的 `StructureTemplateManager` 能从 Spark 内置包读取并实际放置结构模板。
- [x] 每个新增模块均已记录当前工作区客户端及下游原始内容消费者；仅经审计确认无此类消费者的模块不进入 Spark payload。
- [x] 对客户端需要的原生运行时数据，确认目标 1.21.1 注册表/标签/配方同步确实覆盖；不把原生同步视作 Spark 原始内容消费者的替代。
- [x] 明确不支持在世 `/reload` 更新 worldgen 数据。
- [ ] Machine-Max 自定义 processor 能由其代码注册并从 `worldgen/processor_list` 引用，无需修改 Spark 格式。
- [ ] `gradlew --offline test` 通过；真实服务端集成用例通过 `gradlew --offline runGameTestServer` 或项目确定的等价测试车道。

## 9. 未决问题

- 根目录遇到未知扩展名时，报告 ERROR 还是 WARNING 后跳过？不建议静默透传，因为 Minecraft 不会按预期加载。
- worldgen JSON 解析失败首版是否依赖 Minecraft 原生日志，还是补充虚拟资源聚合诊断？本计划不重复实现 Codec 解析器。
- 同路径覆盖是否只依赖依赖图顺序，还是增加显式冲突提示？不得改变现有依赖优先级。
- 若不能稳定构造最小 vanilla Jigsaw fixture，可先以可靠的动态注册表 fixture 验证注入，并将实际结构放置移到 MM 专项 GameTest；但不得省略真实 `SERVER_DATA` 生命周期验收。

## 10. 修订记录

| 版本 | 日期 | 内容 |
|------|------|------|
| v1 | 2026-11-27 | 初稿：规划四个服务端数据包直通模块，明确首次加载时机、无 reload 范围与验收车道。 |
| v2 | 2026-11-27 | 澄清 Spark payload 与 Minecraft 原生同步是独立通道；加入对 MM/外部客户端原始内容消费者的审计要求，不以 Spark 内置 reader 的消费情况决定同步策略，也不改变 recipe 模块契约。 |
| v3 | 2026-11-27 | 落地四个数据根、服务端数据包重建、消费者审计与定向测试；标记真实服务端集成验收待完成。 |
| v4 | 2026-11-27 | 增加内置 3×3 铁块加信标模板与 GameTest；11 项 GameTest 全绿，结构模板读取/放置验收完成，动态注册表验收仍待完成。 |
