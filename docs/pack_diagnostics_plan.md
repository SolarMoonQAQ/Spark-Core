# Spark-Core 内容包诊断系统实施计划

> **状态**: P0–P3 已实现（P4 消费方迁移待做） · **创建**: 2026-10-18 · **修订**: 2026-10-18（v1.4）
>
> **目标版本**: Spark-Core 1.x
>
> **关联**: Machine-Max `docs/plan/计划中/内容包诊断-SparkCore落地后的迁移说明.md`（消费方迁移，不在本文范围；其命令、位置串、Java 构造器验收需随本版同步）

---

## 目录

1. [目标与范围](#1-目标与范围)
2. [现状](#2-现状)
3. [设计](#3-设计)
4. [关键决策](#4-关键决策)
5. [分阶段实施](#5-分阶段实施)
6. [兼容性](#6-兼容性)
7. [风险与缓解](#7-风险与缓解)
8. [验收清单](#8-验收清单)
9. [未决问题与 TODO](#9-未决问题与-todo)
10. [修订记录](#10-修订记录)

---

## 1. 目标与范围

### 目标

内容包读取失败时，作者不需要翻 `latest.log` 就能知道哪个模块、哪个文件、哪条内容出错。实现上分五件事：

- **单一记录**：一条错误就是一个 `PackIssue`，不存在“文件名列表 + 消息列表”这种需要按序号配对的容器。
- **会话按上下文隔离**：用四值 `PackLoadContext` 表示“端 + 来源”，清空、读取、`onFinish`、汇总都围绕这个上下文进行，客户端本地包与服务端下发包互不清空。
- **统一捕获**：模块内 `throw`、构造函数不变量、`DataResult` 错误、资源图解析、依赖排序都落到同一张表；自动打包失败至少在日志与汇总提示中出现（V1 不进玩家列表，见 §3.4）。
- **模块顶层分组**：同一模块的问题放在一起；`packId` / `location` 只做二级定位。
- **玩家可见**：客户端每次进入游戏/重载时一条摘要，服务端每次读取结束时打按模块分组的汇总日志；V1 不做命令。

### 范围

| 纳入 | 不纳入 |
|------|--------|
| `PackIssue` / `PackFrame` / `PackModuleIssues` / `PackIssueSummary` 数据模型与收集器 | JSON 路径追踪（`variants.default.sub_parts.body.mass` 级别的定位） |
| `PackSide` / `PackOrigin` / 四值 `PackLoadContext` 与会话隔离 | 内容错误自动修复、编辑器、Web 面板 |
| 加载器在 `read` / `onStart` / `onFinish` 周围压帧并自动记录 | 服务端到客户端的 issue 同步（列为 P3 可选项） |
| `PackContentException`、`PackCodec`、`DfuText` 三个错误通道 helper，含 Java 互操作 | 遇错中止整包的严格模式 |
| 客户端登录/重载摘要、服务端汇总、配置项 | 命令与 GUI（`/spark package issues` 等，V1 不做） |
| 消费方“模块不写 catch”的契约与迁移说明 | 消费方代码迁移（见 Machine-Max 文档） |
| 收集器/错误通道的单元测试 | 去重（V1 不做，理由见 §3.7）；`PackModuleStartEvent` / `PackModuleFinishEvent`（P3） |

---

## 2. 现状

### 2.1 加载器只有日志

`src/main/kotlin/cn/solarmoon/spark_core/pack/SparkPackLoader.kt` 的 `readPackageContent` 已经对每个文件做了 try/catch：

```kotlin
try {
    module.read(namespace, pathSegments, fileName, content, pack, isClientSide, fromServer)
} catch (e: Exception) {
    LOGGER.error("包 ${pack.meta.id} 读取 ${module.id} 模块失败: $e")
}
```

它接住了 `read` 的异常，但只写日志、不收集、不区分内容错误与代码 bug，也不通知玩家。`onStart` / `onFinish` 的异常则完全没有捕获。Spark-Core 自己的 `ModelModule` 等模块本来就依赖这条路径：模块内不 catch，参数错误直接抛。

现有读取入口用两个布尔 `(isClientSide, fromServer)` 表达四种组合：

| 入口 | `isClientSide` | `fromServer` | 设计后的上下文 |
|------|----------------|--------------|----------------|
| 客户端本地资源包（启动预加载 / 资源重载） | `true` | `false` | `CLIENT_LOCAL` |
| 客户端接收服务端包（配置阶段 / reload payload） | `true` | `true` | `CLIENT_REMOTE` |
| 服务端资源重载 / 启动 | `false` | `true` | `SERVER_LOCAL` |
| `/spark package reload` | `false` | `false` | `SERVER_LOCAL`（修复后） |

同一个 JVM 里客户端侧和服务端侧的读取会写同一份消费方容器。这是当前多个问题的共同前提。

`/spark package reload` 目前传 `(false, false)`，而所有以 `fromServer` 门控的模块（`ModelModule`、`RecipeModule`、Machine-Max 的全部模块）会直接 return；本版把它改为 `SERVER_LOCAL`，即传 `(false, true)`，让 reload 真正读取。

### 2.2 消费方各自实现

以 Machine-Max 为例：`MMDynamicRes` 维护 `exceptions` / `errorFiles` / `errorMessages` 三个 `ArrayList`；12 个内容包模块各自复制同一段 catch：

```kotlin
MMDynamicRes.exceptions.add(e)
MMDynamicRes.errorFiles.add("[HUD]$id")
MMDynamicRes.errorMessages.add(Component.literal(e.message ?: e.toString()))
```

`exceptions` 只有 `add` 与 `clear`，没有读取点。前缀 `[HUD]`、`[Part]`、`[HUD Layout]` 由每个模块手写，格式不统一（还有 `[Materials]`、`[Connectors]` 这类复数形式）。

### 2.3 失败模式

当前 Machine-Max 代码已经比旧版本做了一些兜底，这里只描述仍然成立的问题：

- **三份平行列表**：`exceptions` / `errorFiles` / `errorMessages` 需要按序号配对；`exceptions` 从不被读取，属于死数据。
- **容器不是并发容器**：`ArrayList` 在客户端/服务端读取（reload 的 `prepare` 在后台线程）与主线程展示之间没有同步，存在并发修改风险。
- **清空时机不在读取方**：清空写在消费方的另一个 reload listener（`DataPackReloader.prepare` 调用 `MMDynamicRes.reload()`）里，与 Spark 的读取任务没有先后保证。
- **展示时机不对**：消费方用 `EntityJoinLevelEvent` 触发提示，客户端世界每进来一个 `Player` 实体都会触发一次，旧错误重复出现。

补充说明：当前 MM 模块使用 `e.message ?: e.toString()`，`errorMessageAt` 也有下标保护，因此旧版本描述的 NPE / `IndexOutOfBoundsException` 不再是当前代码的实际表现；本计划不以这两个缺陷作为主要动机，但仍以“单一记录 + 并发安全 + 读取方拥有清空时机”为目标。

### 2.4 已有可复用的捕获点

`readPackageContent` 的 per-file catch 是天然的统一入口。需要补的是：上下文（包/模块/文件）、记录容器、模块顶层分组、错误分类、展示出口。

---

## 3. 设计

### 3.1 数据模型

```kotlin
enum class PackSide { CLIENT, SERVER }

enum class PackOrigin { LOCAL, REMOTE }

/**
 * 一条完整加载路径的“端 + 来源”。
 * 既决定会话清空范围，也决定 PackIssue 的归属。
 */
enum class PackLoadContext(
    val side: PackSide,
    val origin: PackOrigin,
) {
    /** 客户端本地资源包：启动预加载、客户端资源重载 */
    CLIENT_LOCAL(PackSide.CLIENT, PackOrigin.LOCAL),

    /** 客户端接收服务端包：配置阶段 payload、reload payload */
    CLIENT_REMOTE(PackSide.CLIENT, PackOrigin.REMOTE),

    /** 服务端本地包：启动、资源重载、/spark package reload */
    SERVER_LOCAL(PackSide.SERVER, PackOrigin.LOCAL),

    /** 预留：当前无调用；避免以后再加一个“来源”维度 */
    SERVER_REMOTE(PackSide.SERVER, PackOrigin.REMOTE),
    ;

    val isClientSide get() = side == PackSide.CLIENT

    /**
     * 传给 SparkPackModule 的 fromServer，保留现有模块“权威读取”语义：
     * 客户端本地包为 false，其余为 true。
     * 注意：来源看 origin，不要把这个布尔当来源。
     */
    val fromServer get() = this != CLIENT_LOCAL
}

enum class PackSeverity { WARN, ERROR }

data class PackIssue(
    val moduleId: String,          // 顶层分组键
    val packId: String?,           // 非包来源为 null
    val location: String,          // "<namespace>:<moduleId>/<path>"，或注册 id / "*"（模块级）
    val severity: PackSeverity,
    val message: Component,
    val cause: Throwable?,
    val context: PackLoadContext,
)

data class PackModuleIssues(
    val moduleId: String,
    val issues: List<PackIssue>,
) {
    val errors get() = issues.count { it.severity == PackSeverity.ERROR }
    val warnings get() = issues.count { it.severity == PackSeverity.WARN }
}

data class PackIssueSummary(
    val modules: List<PackModuleIssues>,
    val errors: Int,
    val warnings: Int,
    val dropped: Int,
) {
    val isEmpty get() = errors == 0 && warnings == 0 && dropped == 0
}
```

`location` 的规则：

- 包内文件用 `"<namespace>:<moduleId>/<relativePath>"`。`relativePath` 是模块目录下的路径（已含文件名，不含模块名），例如 `machine_max:parts/tank.json`、`spark_core:models/player.json`。这样同一模块内不同子目录、不同模块的同名文件都能区分。
- 非文件来源（配方、研发）用注册 id，模块级用 `"*"`；`packId = null`。
- 模块是顶层分组键；展示时先按模块分组，再用 `packId` / `location` 定位到具体文件。

`PackFrame`：

```kotlin
data class PackFrame(
    val moduleId: String,
    val packId: String?,
    val location: String,
    val context: PackLoadContext,
) {
    @JvmOverloads
    fun issue(severity: PackSeverity, message: Component, cause: Throwable? = null) =
        PackIssue(moduleId, packId, location, severity, message, cause, context)
}
```

### 3.2 会话与帧

`SparkPackIssues` 是所有入口的唯一容器，内部按 `context → moduleId → issues` 组织，模块是顶层键：

```kotlin
object SparkPackIssues {
    private const val CAP_PER_CONTEXT = 200

    private val frames = ThreadLocal.withInitial { ArrayDeque<PackFrame>() }
    private val buckets =
        ConcurrentHashMap<PackLoadContext, ConcurrentHashMap<String, CopyOnWriteArrayList<PackIssue>>>()
    private val counts = ConcurrentHashMap<PackLoadContext, AtomicInteger>()
    private val dropped = ConcurrentHashMap<PackLoadContext, AtomicInteger>()
    private val sessions = ConcurrentHashMap<PackLoadContext, AtomicLong>()
    private val sideSessions = ConcurrentHashMap<PackSide, AtomicLong>()

    @JvmStatic fun begin(context: PackLoadContext) {
        /* 清空该 context 的 bucket / count / dropped；sessionId++，同侧 sideSessionId++ */
    }

    @JvmStatic fun <T> withFrame(frame: PackFrame, block: () -> T): T {
        val stack = frames.get()
        stack.push(frame)
        try { return block() } finally { stack.pop() }
    }

    @JvmStatic fun currentFrame(): PackFrame? = frames.get().peek()

    @JvmStatic @JvmOverloads
    fun warn(message: Component, cause: Throwable? = null) {
        val frame = currentFrame() ?: return RuntimeIssueLog.warn(message, cause)
        report(frame.issue(PackSeverity.WARN, message, cause))
    }

    @JvmStatic @JvmOverloads
    fun error(message: Component, cause: Throwable? = null): Nothing =
        throw PackContentException(message, cause)

    @JvmStatic fun report(issue: PackIssue) { /* 限流、追加、按 severity 打日志，内部整体 try/catch */ }

    @JvmStatic fun report(
        moduleId: String,
        location: String,
        severity: PackSeverity,
        message: Component,
        cause: Throwable?,
        context: PackLoadContext,
    ) = report(PackIssue(moduleId, null, location, severity, message, cause, context))

    @JvmStatic fun snapshot(context: PackLoadContext): List<PackIssue>
    @JvmStatic fun snapshot(side: PackSide): List<PackIssue>
    @JvmStatic fun byModule(side: PackSide): List<PackModuleIssues>
    @JvmStatic fun summary(side: PackSide): PackIssueSummary
    @JvmStatic fun sessionId(context: PackLoadContext): Long
    @JvmStatic fun sideSessionId(side: PackSide): Long    // 该侧任一上下文 begin 时递增
    @JvmStatic fun logSummary(context: PackLoadContext)   // 仅服务端上下文使用
}
```

规则：

- **帧是线程本地的栈**，`withFrame` 在 `finally` 里弹出；嵌套帧（模块级 → 文件级 → 条目级）按栈顶取。
- **`begin(context)` 只清该上下文**：`CLIENT_LOCAL` 与 `CLIENT_REMOTE` 是两个独立 bucket，客户端本地资源重载不会清掉服务端下发包的记录；`SERVER_LOCAL` 与 `SERVER_REMOTE` 同理。
- **`begin` 的调用点**：`SparkPackLoader.beginSession(context)` 在每条完整加载路径的入口调用一次，内部执行 `SparkPackIssues.begin(context)` 并清空本上下文的失败模块集合。`readPackageGraph`、`acceptRemote`、`readPackageContent`、`injectPackageContent` 都不清空。
- **帧不跨线程**：reload 的 `prepare` 在后台 executor 执行、`apply` 在主线程执行；跨线程工作必须在提交前 `val frame = currentFrame()`，在异步任务里用 `frame.issue(...)` 显式 `report`。
- **`report` 永不抛**：超过每上下文上限时只累加 `dropped`；不做去重（见 §3.7）。
- **模块顶层**：`buckets[context][moduleId]` 是实际存储结构，`byModule` / 汇总 / 通知都直接按模块迭代。

上下文与调用方：

| 上下文 | 调用方 | 传给模块的 `(isClientSide, fromServer)` |
|--------|--------|------------------------------------------|
| `CLIENT_LOCAL` | 客户端首次预加载、客户端资源重载 | `(true, false)` |
| `CLIENT_REMOTE` | `SparkPackagePayload`、`SparkPackageReloadPayload` | `(true, true)` |
| `SERVER_LOCAL` | 服务端首次预加载、服务端资源重载、`/spark package reload` | `(false, true)` |
| `SERVER_REMOTE` | 预留，当前无调用 | `(false, true)` |

`context` 描述本次读取路径，不描述每个包的真实来源：例如 `CLIENT_REMOTE` 的 payload 会把客户端图里的本地包也按 `fromServer = true` 读一遍。记录以 `packId` 定位包，以 `context` 区分本次是哪条路径。

`SparkPackModule` 的签名保持不变：加载器从上下文派生 `isClientSide` / `fromServer` 再调用模块。`fromServer` 保留“是否权威读取”的旧语义，不再作为来源判断。

### 3.3 错误通道

#### 3.3.1 构造器与 `xmap`

内容对象的不变量检查留在构造函数里，直接抛带 `Component` 的异常：

```kotlin
class PackContentException @JvmOverloads constructor(
    val userMessage: Component,
    cause: Throwable? = null,
) : RuntimeException(userMessage.string, cause, false, false)
```

`writableStackTrace = false`：这是内容错误，不是代码 bug，堆栈只会把日志撑长。消息用 `Component` 而不是 `String`，保留翻译能力；`@JvmOverloads` 让 Java 消费方可以直接 `new PackContentException(Component...)`。

#### 3.3.2 `DataResult`

DFU 的 `Codec#decode` 在缺必填字段、类型不符时返回 `DataResult.Error`，本身不抛。模块不应裸用 `.orThrow`（它抛 `IllegalStateException`，与真正的代码 bug 无法区分），改用 helper：

```kotlin
object PackCodec {

    @JvmStatic
    fun parse(content: ByteArray): JsonElement = try {
        JsonParser.parseString(String(content, StandardCharsets.UTF_8))
    } catch (e: JsonParseException) {
        throw PackContentException(
            Component.translatableWithFallback(
                "spark_core.pack.error.json_syntax",
                "JSON syntax error: %s",
                e.message ?: "",
            )
        )
    }

    @JvmStatic @JvmOverloads
    fun <T> read(
        codec: Codec<T>,
        content: ByteArray,
        ops: DynamicOps<JsonElement> = JsonOps.INSTANCE,
    ): T = codec.decode(ops, parse(content))
        .getOrThrow { message -> PackContentException(DfuText.toComponent(message)) }
        .first

    @JvmStatic @JvmOverloads
    fun <T> read(
        codec: Codec<T>,
        json: JsonElement,
        ops: DynamicOps<JsonElement> = JsonOps.INSTANCE,
    ): T = codec.decode(ops, json)
        .getOrThrow { message -> PackContentException(DfuText.toComponent(message)) }
        .first
}
```

`Codec#decode` 在 DFU 8.0.16 返回 `DataResult<Pair<A, T>>`，`getOrThrow` 返回 `Pair`，所以 `.first` 是取解码结果，不是错误用法。`DataResult.getOrThrow(Function)` 在该版本也存在。

`DfuText` 处理 DFU 消息的两个问题：

- 缺字段的原始消息形如 `No key mass in {……整个 JSON……}`，`JsonOps` 会把输入对象的 `toString` 拼进去。展示前提取字段名并截断，避免整份文件进聊天框。
- DFU 不提供 JSON 路径，只能精确到“缺哪个字段”；精确到哪个 variant / sub_part 由逐项校验负责。

```kotlin
object DfuText {
    private val NO_KEY = Regex("""No key (\S+) in """)

    @JvmStatic
    fun toComponent(raw: String): Component {
        NO_KEY.find(raw)?.let {
            return Component.translatableWithFallback(
                "spark_core.pack.error.missing_field",
                "Missing required field: %s",
                it.groupValues[1],
            )
        }
        return Component.literal(raw.take(300))
    }
}
```

`translatableWithFallback` 保证在语言键尚未加载（或专用服务器没有 mod 语言文件）时，日志里仍是可读的英文，而不是裸 key。对应的 lang 键在 P1 就要加入，不能留到 P2。

#### 3.3.3 其他异常

loader 的通用 `catch (e: Exception)` 记录为 ERROR 并保留 `cause`，日志里带堆栈。这表示模块代码有 bug，不是内容写错。

#### 3.3.4 错误粒度

- 一个文件解析成一个条目时，`error` 抛出即跳过该文件，loader 继续下一个文件。
- `Codec.unboundedMap` 是整体的：一个 sub_part 缺字段会让整个 map 失败，最终整个文件失败。这是文件级粒度的自然结果。
- 需要“只跳过坏条目”时，模块把嵌套 map 拆成逐项 decode，在条目循环里 catch 一次并 `report`（非抛出），作用域停在条目上。这是唯一值得写 catch 的地方。

### 3.4 加载器接入点

| 位置 | 帧 | 记录 |
|------|----|------|
| `beginSession(context)` | — | `SparkPackIssues.begin(context)` + 清空失败模块集合 |
| `module.onStart` | 模块级：`location = "*"` | 异常 → ERROR；该模块本次跳过 `read` 与 `onFinish` |
| `module.read`（每个文件） | 文件级：`location = "<ns>:<moduleId>/<path>"` | `PackContentException` → ERROR；其他异常 → ERROR + 堆栈 |
| `module.onFinish` | 模块级：`location = "*"` | 异常 → ERROR |
| `readPackageGraph` 的 `readAsPackage` catch | `moduleId = "graph"`，`location = 包文件名` | ERROR |
| `resolveLoadOrder` 失败 | `moduleId = "graph"`，`location = "-"` | ERROR（本次一个包都没加载） |
| `injectPackageContent` 结束 | — | 服务端汇总日志 |

签名与调用：

```kotlin
fun beginSession(context: PackLoadContext)
fun readPackageGraph(context: PackLoadContext)
fun readPackageContent(context: PackLoadContext)
fun injectPackageContent(context: PackLoadContext)
```

`beginSession` 是唯一清空入口；`readPackageGraph` / `readPackageContent` / `injectPackageContent` 都只使用传入的上下文。

模块级与文件级的接入骨架：

```kotlin
fun readPackageContent(context: PackLoadContext) {
    val orderedPacks = try {
        graph.resolveLoadOrder()
    } catch (e: Exception) {
        SparkPackIssues.report(
            PackIssue("graph", null, "-", PackSeverity.ERROR, messageOf(e), e, context)
        )
        return
    }

    modules.values.forEach { module ->
        val moduleFrame = PackFrame(module.id, null, "*", context)
        var started = false
        SparkPackIssues.withFrame(moduleFrame) {
            try {
                module.onStart(context.isClientSide, context.fromServer)
                started = true
            } catch (e: Exception) {
                failedModules.getOrPut(context).add(module.id)
                SparkPackIssues.report(moduleFrame.issue(PackSeverity.ERROR, messageOf(e), e))
            }
        }
        if (!started) return@forEach

        for (pack in orderedPacks) {
            for ((namespace, moduleMap) in pack.entries) {
                val files = moduleMap[module.id] ?: continue
                for ((path, content) in files) {
                    val parts = path.split("/")
                    val fileName = parts.last()
                    val pathSegments = if (parts.size > 1) parts.dropLast(1) else emptyList()
                    val fileFrame = PackFrame(
                        module.id,
                        pack.meta.id.toString(),
                        "$namespace:${module.id}/$path",
                        context,
                    )
                    SparkPackIssues.withFrame(fileFrame) {
                        try {
                            module.read(
                                namespace, pathSegments, fileName, content, pack,
                                context.isClientSide, context.fromServer,
                            )
                        } catch (e: PackContentException) {
                            SparkPackIssues.report(fileFrame.issue(PackSeverity.ERROR, e.userMessage, e.cause))
                        } catch (e: Exception) {
                            SparkPackIssues.report(fileFrame.issue(PackSeverity.ERROR, messageOf(e), e))
                        }
                    }
                }
            }
        }
    }
}

private fun messageOf(e: Throwable): Component =
    (e as? PackContentException)?.userMessage ?: Component.literal(e.message ?: e.toString())
```

`injectPackageContent` 在 `apply` / payload 收尾时调用，跳过 `onStart` 失败的模块：

```kotlin
fun injectPackageContent(context: PackLoadContext) {
    val failed = failedModules[context].orEmpty()
    modules.values.forEach { module ->
        if (module.id in failed) return@forEach
        val frame = PackFrame(module.id, null, "*", context)
        SparkPackIssues.withFrame(frame) {
            try {
                module.onFinish(context.isClientSide, context.fromServer)
            } catch (e: Exception) {
                SparkPackIssues.report(frame.issue(PackSeverity.ERROR, messageOf(e), e))
            }
        }
    }
    failedModules.remove(context)

    // P2 起：在汇总前 post，订阅者可以在这里补报问题
    try {
        NeoForge.EVENT_BUS.post(PackAllFinishedEvent(context))
    } catch (e: Exception) {
        RuntimeIssueLog.error(Component.literal("内容包完成事件处理失败"), e)
    }

    if (context.side == PackSide.SERVER) SparkPackIssues.logSummary(context)
}
```

`SparkPackResourceLoader.loadModule` 的自动打包失败只写日志，不进玩家列表：它多半是打包/路径问题，不是内容 JSON 问题。但 `/spark package reload` 会调用 `SparkPackResourceLoader.reload()`，此时它已经发生在会话内，因此至少要在日志里保留 WARN，并在服务端汇总之外另给一行提示；是否进入 `PackIssue` 留到 P3 再评估。

### 3.5 模块侧契约

- **不写 try/catch**：解码失败用 `PackCodec.read`，语义错误 `throw PackContentException(...)`，警告 `SparkPackIssues.warn(...)`。唯一允许的 catch 是 §3.3.4 的条目循环。
- **不拼前缀、不维护列表**：包、模块、文件由帧提供。
- **不加签名参数**：`SparkPackModule.read` 的签名保持不变；上下文由加载器派生。
- **解析留在 `read`**：`onFinish` 是模块级帧，没有文件归属。确实需要延后处理的模块，必须自己保存 `(packId, namespace, moduleId, path)` 并逐条 `report`，不能依赖帧。
- **`onStart` 失败 = 跳过该模块**：本次上下文内的 `read` 与 `onFinish` 都不再执行，只记录一条 ERROR，避免半初始化状态继续写注册表。
- **多条目文件**：条目循环内 catch 一次并 `report`，不要在外层 catch。
- **警告与错误的判据**：条目还能不能进注册表。能进 → WARN；不能进 → ERROR。
- **非包来源**：配方、研发等显式 `report(moduleId, location, severity, message, cause, context)`，`packId = null`；只在服务端调用。
- **线程**：不要在 `read` 返回后继续从其他线程 `report` 而不捕获帧；帧是 ThreadLocal，异步任务必须显式传递 `PackFrame`。
- **汇总边界**：服务端汇总与客户端摘要只保证包含本次 `readPackageContent` / `injectPackageContent` 期间记录的问题。消费方在自己的 reload listener 中执行、且晚于 Spark 汇总的额外检查（例如 MM 的配方/研发重建）不保证出现在汇总行里；这些检查应显式 `report`，并接受“可能只进 bucket、不进本次汇总”的时序。P2 起的 `PackAllFinishedEvent` 提供汇总前 Hook（消费方把检查移进事件即可进本次汇总）；P3 再补模块级事件。
- **消息**：优先使用带 fallback 的 `Component.translatableWithFallback`，保证服务端日志可读；不要输出裸 key。
- **Java**：Kotlin 默认参数对 Java 不可见，新 API 必须带 `@JvmOverloads` / `@JvmStatic`（见 §3.8）。
- **不重复上报**：同一失败只 `report` 一次；V1 不去重，重复上报会原样进入列表。

### 3.6 展示

#### 客户端

Spark-Core 的客户端类统一由 `SparkClientEventRegister` 在 `FMLEnvironment.dist.isClient` 分支里注册。新增 `PackIssueNotifier`：

- 监听 `ClientPlayerNetworkEvent.LoggingIn`，登录时调用一次 `notifyIfAny(PackSide.CLIENT)`，作为兜底。
- 订阅 `PackAllFinishedEvent`（`context.side == CLIENT` 时），在事件处理器里调用 `notifyIfAny(PackSide.CLIENT)`。这样 `SparkPackagePayload` / `SparkPackageReloadPayload` / `SparkPackLoaderApplier.client()` 不需要直接引用客户端类。
- `notifyIfAny(side)` 用 `SparkPackIssues.sideSessionId(side)` 判断本侧会话是否已通知：同一会话只发一次，既避免“登录 + 刚完成的 payload”重复弹，也避免 `CLIENT_LOCAL` / `CLIENT_REMOTE` 各发一条。摘要本身是 `summary(CLIENT)`，会把两个上下文合并展示。
- 退出（`LoggingOut`）时清空两个 CLIENT 上下文并重置通知器的 `lastNotified`，避免下次登录看到上一次服务器的旧记录。
- 配置 `packIssueNotification`：`OFF` / `SUMMARY`（默认） / `DETAIL`。
  - `SUMMARY`：一条消息，`内容包有 N 个错误、M 个警告，涉及 K 个模块：parts(2)、models(1)… 详情见日志。`
  - `DETAIL`：按模块各一行，每模块最多 5 条、总共最多 10 行；超出的提示查看日志。V1 没有命令，不要在聊天里倾倒全部记录。
- 没有记录时不发任何消息。

#### 服务端

`injectPackageContent(SERVER_LOCAL)` 结束时：

- `SparkPackIssues.summary(SERVER)` 打一条 INFO，按模块分组：`内容包读取完成：3 个模块有问题（5 错误 / 2 警告，另有 1 条被截断）：parts(3)、models(2)、recipe(2)`。
- 每条 ERROR 在记录时已带堆栈打到日志（`PackContentException` 只打一行；其他异常保留堆栈，限前 50 条，其余折叠为一行提示）。
- **汇总不保证包含消费方延后上报的检查**（如 MM 的配方/研发），按 §3.5 的“汇总边界”处理。

#### 加载阶段事件（`PackAllFinishedEvent` 于 P2，模块级事件于 P3）

`PackAllFinishedEvent(context)` 在 P2 就要有：`injectPackageContent` 在所有模块 `onFinish` 之后、`logSummary` 之前 post，`PackIssueNotifier` 订阅它做客户端摘要。它同时也是消费方在汇总前做额外检查的通用入口。

P3 再补模块级事件。事件只做观察/扩展，不取消、不修改内容：

```kotlin
class PackModuleStartEvent(
    val context: PackLoadContext,
    val moduleId: String,
) : Event()

class PackModuleFinishEvent(
    val context: PackLoadContext,
    val moduleId: String,
    val success: Boolean,
) : Event()

class PackAllFinishedEvent(
    val context: PackLoadContext,
) : Event()
```

- `PackModuleStartEvent`：在调用 `onStart` 之前 post；可选，P3 视消费者需求再定，V1 不承诺。
- `PackModuleFinishEvent`：模块处理结束后 post（正常路径在 `onFinish` 之后；`onStart` 失败、模块被跳过时也 post 一次 `success = false`）；`success` 表示 `onStart` / `read` / `onFinish` 是否全部成功，失败详情走 `SparkPackIssues`。
- `PackAllFinishedEvent`：当前 `PackLoadContext` 内所有模块 `onFinish` 之后、`logSummary` 之前 post；语义是“本次加载路径的所有模块处理完毕”，不是所有端/所有上下文一起完成。订阅者在这里 `report` 的记录会进本次汇总。
- **不做 per-file `read` 事件**：`read` 是每文件调用一次，per-file 事件有数量、后台线程和原始字节的问题；消费方需要逐文件校验时，正确做法是写 `SparkPackModule`。如果将来确有需求，再加 metadata-only 的 `PackFileReadEvent(context, moduleId, packId, namespace, path, success)`，不带 `content`；折中方案是每模块一次的 `PackModuleReadFinishedEvent`。

事件契约：

- 都走 `NeoForge.EVENT_BUS`，普通 `Event`；只通知，不取消、不改内容。
- 订阅者异常由 loader 捕获并记录，不能中断加载；`post` 外面包一层 try/catch。
- 线程：`PackModuleStartEvent` 在 reload `prepare` 的后台线程（payload / 命令路径在主线程）；`read` 本身不单独发事件。`PackModuleFinishEvent` 与 `PackAllFinishedEvent` 在 `apply` / payload / 命令的主线程。订阅者不得从后台线程碰客户端状态。
- 顺序：`PackModuleFinishEvent` 在每个模块 `onFinish` 后；`PackAllFinishedEvent` 在所有模块后、`logSummary` 前。`PackIssueNotifier` 用 `EventPriority.LOWEST`，确保在其他订阅者 `report` 之后读 summary。
- 事件只带 `context` / `moduleId` / `success` 等元数据，不传 `ByteArray` 或可变内部结构。
- 事件不替代模块逻辑；MM 的配方/研发如果依赖 `RecipeManager` 的 apply 时序，仍要在自己的 listener 里做，汇总边界不变。

#### 命令（V1 不做）

`/spark package issues` 及相关分页、权限、客户端命令注册全部移出 V1 范围。原因是：命令涉及“服务端命令看不到客户端 bucket”的双端注册问题，以及 `/spark package` 根节点权限 2 与“普通玩家可看”的冲突；当前展示需求用登录/重载摘要即可满足。命令与 GUI 放到 §9 的后续项。

### 3.7 上限、排序与线程安全

- **上限**：每个 `PackLoadContext` 200 条；超出只累加 `dropped`，汇总和通知显示被截断数量。
- **不做去重**：会话按上下文清空，`/spark package reload` 不会累加；真实重复主要来自消费方重复上报，应由 §3.5 的契约约束。去重键如果用 `message.string`，会受语言/翻译键影响，反而会把同一文件里两个不同字段的 `missing_field` 合并；V1 不做去重，避免隐藏信息。若以后发现确有必要，使用调用方显式提供的 key，而不是渲染文本。
- **排序**：模块 → `packId`（null 排最前）→ `location` → `severity`（ERROR 在前）。模块是顶层分组，保证多次 reload 的输出可 diff。
- **线程安全**：bucket 用 `CopyOnWriteArrayList`，上下文/模块 map 用 `ConcurrentHashMap`；`Component` 按不可变对象对待；`snapshot` 返回副本。

### 3.8 Java 互操作

- `PackContentException` 的构造器加 `@JvmOverloads`，Java 可直接写 `new PackContentException(Component.translatable(...))`。
- `PackCodec` / `DfuText` / `RuntimeIssueLog` 的方法加 `@JvmStatic`，避免 Java 写 `PackCodec.INSTANCE`。
- `PackCodec.read` 的 `ops` 默认参数加 `@JvmOverloads`，Java 可调 `PackCodec.read(codec, content)`。
- `SparkPackIssues.warn` / `error` 的 `cause` 默认参数加 `@JvmOverloads`；`report` 保持无默认参数，Java 显式传 `null`。
- `PackFrame.issue` 的 `cause` 默认参数加 `@JvmOverloads`。
- `SparkPackModule` 接口保持 Java 可实现；两个布尔参数不变。

### 3.9 实施前检查

以下 5 项不改变设计，但建议在 P0 开工时一并定稿，避免实现时临时分叉：

1. **依赖排序失败时跳过 `onFinish`**：`resolveLoadOrder` 失败时，`readPackageContent` 在记录 graph ERROR 后，把当前 context 的所有模块加入 `failedModules`；`injectPackageContent` 因此不会在没有 `onStart` 的情况下调用 `onFinish`。若实现选择不跳过，必须在 §3.5 契约里写明“`onFinish` 可能在没有 `onStart` 时被调用”。
2. **客户端通知的入口**：`injectPackageContent` 是 common 代码，P2 不要直接引用客户端 `PackIssueNotifier`。在 `injectPackageContent` 末尾 post 一个 common 的 `PackAllFinishedEvent(context)`，`PackIssueNotifier` 只在客户端订阅；`SparkPackagePayload` / `SparkPackageReloadPayload` / `SparkPackLoaderApplier.client()` 不再直接引用客户端类。
3. **`beginSession` 顺序**：入口固定为 `beginSession(context)` → `initialize(...)`（如需要）→ `readPackageGraph(context)` → `readPackageContent(context)` → `injectPackageContent(context)`；`beginSession` 只清诊断状态，不碰 loader 的 `graph` / `modules`。
4. **日志与上限**：`SparkPackIssues.report` 使用 `SparkCore.logger("内容包诊断")`；每个 context 的 ERROR 堆栈限前 50 条，`PackContentException` 只打一行；`CAP_PER_CONTEXT = 200`。
5. **测试隔离**：`SparkPackIssues` 是 object，单元测试用 `begin(context)` 清状态，不依赖 JVM 重启；必要时加仅供测试的 `reset()`。

---

## 4. 关键决策

| 决策 | 理由 | 备选 |
|------|------|------|
| 四值 `PackLoadContext` 取代 `PackSide` 分桶 | 一次解决“端 + 来源”组合、客户端本地/远端互清、reload 语义；杜绝 `(false, false)` 这种非法组合 | 保持两个布尔 + 派生 `side`（同侧两来源仍会互清） |
| 模块作为顶层分组 | 一类模块的问题集中展示；`packId` / `location` 只做二级定位 | 按 pack 分组（同一模块跨包分散） |
| `begin(context)` 只清本上下文 | `CLIENT_LOCAL` 与 `CLIENT_REMOTE` 互不影响；服务端同理 | 单一 side bucket（同侧两来源会互清） |
| `SparkPackLoader.readPackageContent(context)` 取代两布尔 | 调用点无法再传错组合；`/spark package reload` 顺带修好 | 保留两布尔 + 运行时校验 |
| V1 不做去重 | 会话按上下文清空，reload 不累加；去重会受语言影响、隐藏不同字段的同文案错误 | 去重键（需要显式、语言无关的 key） |
| V1 不做命令 | 展示只需登录/重载摘要；命令涉及权限与双端注册，收益低 | 命令 + 分页（后续 P3+） |
| 汇总不保证包含消费方延后检查 | Spark 汇总早于 MM 的 `LOWEST` reload listener；P2 的 `PackAllFinishedEvent` 提供汇总前 Hook，消费方需把检查移进事件 | 延迟汇总 / 模块级事件 |
| `SparkPackModule` 签名不变 | 消费方无需改接口；上下文由 loader 派生 | 改签名传 context（改动面大，另行评估） |
| `@JvmOverloads` / `@JvmStatic` | MM 侧有 Java 消费方（`SubPartAttr` 等） | 只面向 Kotlin（会挡住 MM 的 Java 迁移示例） |

---

## 5. 分阶段实施

> **实现状态（v1.4）**：P0、P1、P2 已完成；P3 只落地了模块级事件（`PackModuleStartEvent` / `PackModuleFinishEvent`），JSON dump 与服务端 issue 同步仍未做；P4 消费方迁移不在本仓库。
>
> 与计划文本的实现差异：
> - `SparkPackIssues.reset()` 作为测试隔离入口加入（仅测试调用）。
> - 服务端无问题时也会打一条 `Content pack read finished: no issues.`，便于确认汇总确实执行。
> - JSON 语法错误不再把 `JsonParseException` 作为 cause 传入，保证只打一行日志；行列号已包含在消息里。
> - `SUMMARY` 模式在有截断时追加一条截断提示（与 `DETAIL` 一致）。
> - `PackModuleStartEvent` / `PackModuleFinishEvent` 与 P2 一起实现，加载器已在 `onStart` / `onFinish` 前后发布。
> - `readPackageGraph` 跳过隐藏目录（如 `.docs`）和没有 `meta.json` 的普通目录，避免把文档目录误报成 graph 错误。

### P0 — 收集骨架、上下文与 reload 修复

新增：

- `pack/diagnostic/PackIssue.kt`：`PackSide`、`PackOrigin`、`PackLoadContext`、`PackSeverity`、`PackIssue`、`PackModuleIssues`、`PackIssueSummary`
- `pack/diagnostic/PackFrame.kt`
- `pack/diagnostic/PackContentException.kt`（`@JvmOverloads`）；P0 的 loader 专用 catch 依赖它
- `pack/diagnostic/SparkPackIssues.kt`
- `pack/diagnostic/RuntimeIssueLog.kt`

修改：

- `pack/SparkPackLoader.kt`：`beginSession`、`readPackageGraph/readPackageContent/injectPackageContent(context)`、`withFrame`、两类 catch、失败模块集合、服务端汇总
- `pack/SparkPackLoaderApplier.kt`：首次预加载与重载分别使用 `CLIENT_LOCAL` / `SERVER_LOCAL`，入口调 `beginSession`
- `command/ReloadPackageCommand.kt`：`beginSession(SERVER_LOCAL)`，并传 `SERVER_LOCAL`（修复 `fromServer` 门控导致模块不读取的问题）
- `pack/sync/SparkPackagePayload.kt`、`pack/sync/SparkPackageReloadPayload.kt`：`beginSession(CLIENT_REMOTE)`，读取/注入都传 `CLIENT_REMOTE`

验收：

- `gradlew.bat build`（Windows；Linux/macOS 为 `./gradlew build`）通过。
- 故意放一个坏包，日志出现 `[模块] 文件 → 消息` 的结构化行与按模块分组的汇总。
- `/spark package reload` 现在能真正读取模块（用坏 `models` 文件验证；若构建包含 MM，也可用坏 `parts` 文件），连续三次记录数不累加。
- 好包不产生任何 ERROR/WARN 记录。
- 收集器单元测试：上限、排序、并发 report、上下文隔离。

### P1 — 错误通道、Java 互操作与试点迁移

新增：

- `pack/diagnostic/PackCodec.kt`（`@JvmStatic` / `@JvmOverloads`）
- `pack/diagnostic/DfuText.kt`（`@JvmStatic`）

修改：

- 试点迁移 `ModelModule`、`AnimationModule`、`RecipeModule`：`PackCodec.read` 替换 `.orThrow` / 裸 `JsonParser`，路径校验改 `PackContentException`。没有试点模块，P1 验收没有可验证对象。
- `assets/spark_core/lang/{en_us,zh_cn}.json`：`spark_core.pack.error.missing_field`、`spark_core.pack.error.json_syntax`。这些键必须在 P1 就位，不能留到 P2。

验收：

- 缺必填字段 → 一条 ERROR，消息是“缺少必填字段 X”。
- JSON 语法错误 → 一条 ERROR，带行列号。
- 构造函数抛 `PackContentException` → 一条 ERROR，无堆栈。
- 模块代码 NPE → 一条 ERROR，带堆栈。
- 同一文件缺两个不同字段 → **两条**记录（V1 不去重）。
- Java 侧可直接 `new PackContentException(Component...)`、`PackCodec.read(codec, content)` 编译通过。
- `DfuText` / `PackCodec` 单元测试：缺字段、语法错误、类型不符、长消息截断。

### P2 — 展示

新增：

- `pack/diagnostic/event/PackAllFinishedEvent.kt`
- `pack/diagnostic/client/PackIssueNotifier.kt`，注册到 `SparkClientEventRegister`

修改：

- `SparkCoreConfig` 的 `packIssueNotification`
- `assets/spark_core/lang/{en_us,zh_cn}.json` 的摘要/详情键
- `pack/SparkPackLoader.kt`：`injectPackageContent` 末尾、`logSummary` 之前 post `PackAllFinishedEvent`
- 客户端 `PackAllFinishedEvent` 订阅 + `LoggingIn` 兜底（不再从 payload/common 代码直接调用通知器）
- 服务端 `injectPackageContent(SERVER_LOCAL)` 结束后的按模块汇总

验收：

- 客户端进入游戏只出现一条摘要（有记录时）；没有记录时不出现。
- 每次客户端重载/payload 完成，若有新记录，出现一条摘要；同一 session 不重复。
- `OFF` 时不发聊天提示，日志仍完整。
- 服务端启动/重载日志有按模块分组的汇总。
- 专用服务器上，客户端摘要只反映 CLIENT 侧记录；服务端配方/研发错误只在服务端日志里（且受“汇总边界”限制）。

### P3 — 可选增强

- `logs/spark_core-pack-issues.json`：本次会话的完整记录，方便作者直接打开。
- 服务端把 SERVER 侧的 issue 摘要通过 payload 发给客户端，使专用服务器上的配方/研发错误也能在游戏内看到。
- `pack/diagnostic/event/PackModuleStartEvent.kt`、`PackModuleFinishEvent.kt`：模块级加载阶段事件；`PackAllFinishedEvent` 已在 P2 落地，P3 开放给消费方在汇总前做额外检查；per-file `read` 事件按需再加。
- `withScope("variant=default")` 形式的条目级作用域。
- DFU 常见错误消息的本地化映射。
- 命令/GUI（`/spark package issues` 或客户端面板）；重新评估权限与双端注册方案。

### P4 — 消费方迁移

不在本文范围。Machine-Max 的迁移清单见其 `docs/plan/计划中/内容包诊断-SparkCore落地后的迁移说明.md`。该文档需要随本版同步：删除命令相关验收、更新位置串格式、确认 Java 构造器使用 `@JvmOverloads`、接受“汇总不保证包含配方/研发错误”。

---

## 6. 兼容性

- `SparkPackModule` 接口与 `read` 签名不变；现有模块不需要改代码就能被收集（它们的异常原本就会冒泡到 `SparkPackLoader`）。
- `SparkPackLoader.readPackageContent` / `injectPackageContent` 的参数从两个布尔改为 `PackLoadContext`；`readPackageGraph` 从单个 `isClientSide` 改为 `PackLoadContext`；并新增 `beginSession(context)`。这是 loader 的 API 变化，外部若有直接调用需要同步。模块侧不受影响。
- `/spark package reload` 的行为会变化：从“多数模块直接 return”变为真正按 `SERVER_LOCAL` 读取。这是本版有意修复的行为变化。
- 新增的 `PackCodec` / `PackContentException` 是加法 API；消费方可以按自己的节奏迁移。
- `SparkPackLoader` 的日志格式会变化：从 `包 X 读取 Y 模块失败: ...` 变为 `[Y] X/location → message`。依赖旧日志文本的外部脚本需要同步。
- 客户端提示默认开启摘要；`packIssueNotification = OFF` 时不发聊天提示，日志仍完整。
- V1 不提供 `/spark package issues`；依赖该命令的迁移文档/脚本需要等后续版本。

---

## 7. 风险与缓解

| 风险 | 缓解 |
|------|------|
| ThreadLocal 泄漏 | `withFrame` 在 `finally` 弹出；异步任务要求显式捕获 `currentFrame()` |
| 客户端本地包与服务端下发包互相清空 | 四值 `PackLoadContext`，`begin(context)` 只清本上下文 |
| `PackLoadContext` 被误用（如 future 的 `SERVER_REMOTE`） | 枚举注释标明“预留、当前无调用”；`fromServer` 由枚举派生，调用方不再手写布尔 |
| 坏包产生大量记录拖慢日志 | 每上下文 200 条上限 + 汇总；ERROR 堆栈只打前 50 条 |
| 不做去重导致同一问题重复出现 | 契约要求“同一失败只 report 一次”；条目级问题本来就应该按条目各记一条 |
| 模块仍自己 catch，导致双份记录 | 契约写明“不 catch”；review 时检查 `errorFiles` 类调用 |
| 运行时构造误报 | `warn` 无帧时退化为普通日志；`error` 保留抛出语义 |
| 消费方延后检查不进汇总 | §3.5 的“汇总边界”明确；P2 的 `PackAllFinishedEvent` 提供 Hook，P3 补模块级事件 |
| 客户端摘要重复/过早 | `sideSessionId` + `LoggingIn` / `injectPackageContent` 双时机；`LoggingOut` 清空 |
| Java 默认参数不可见 | `@JvmOverloads` / `@JvmStatic`；MM 迁移示例按 Java 编译验证 |
| 客户端/服务端共享 `graph` / `modules` | 分上下文只隔离记录，不隔离 loader 状态；这是既有问题，本计划不扩大范围，后续单独评估 |
| 没有命令时作者定位不便 | 服务端日志保留结构化行 + 汇总；`DETAIL` 模式按模块展示前几条；P3 再考虑 JSON dump/GUI |

---

## 8. 验收清单

- [x] `gradlew.bat build` 通过（Spark-Core 与复合构建的 Machine-Max）。
- [ ] 好包：登录无提示，服务端汇总显示无问题。
- [ ] 缺必填字段：聊天摘要 1 条，服务端日志能看到模块、文件与“缺少必填字段 X”。
- [ ] JSON 语法错误：消息带行列号。
- [ ] 构造函数不变量：消息是翻译文本，不是裸 key。
- [ ] 模块代码 NPE：一条 ERROR，带堆栈。
- [ ] 同一文件缺两个不同字段：两条记录（V1 不去重）。
- [x] 坏包不导致服务器启动失败。
- [ ] 坏 `models`（或 MM `parts`）文件后执行 `/spark package reload`：能记录到该模块；连续三次记录数不累加。
- [ ] 客户端退出再进：旧记录不重复提示。
- [ ] `packIssueNotification = OFF`：无聊天提示，日志仍完整。
- [x] Java 侧 `new PackContentException(Component...)` 与 `PackCodec.read(codec, content)` 编译通过。
- [x] 服务端汇总按模块分组；明确“配方/研发等消费方延后检查不保证包含”。
- [x] V1 没有 `/spark package issues`，文档与验收不依赖命令。

---

## 9. 未决问题与 TODO

1. **JSON 路径**：DFU 没有路径信息，目前只能精确到字段名。未来可考虑包一层 `DynamicOps` 在 `MapLike` 上记录路径。
2. **DFU 消息本地化**：`DfuText` 目前只处理 `No key ... in ...`；其他常见错误（类型不符、数组长度）是否逐条映射，按实际作者反馈补充。
3. **服务端 issue 是否同步给客户端**：V1 不做；专用服务器上的配方/研发错误只有服务端日志。P3 再决定 payload 形态（字符串列表或 `Component` 流）。
4. **模块级加载事件**：`PackModuleStartEvent` / `PackModuleFinishEvent` 已在 v1.4 随 P3 落地；`PackAllFinishedEvent` 在 P2 提供汇总前 Hook。V1 不做 per-file `read` 事件；必要时加每模块一次的 `PackModuleReadFinishedEvent`，或 metadata-only 的 `PackFileReadEvent`。
5. **命令/GUI**：V1 不做。后续若恢复 `/spark package issues`，需要重新设计：服务端命令与客户端命令分开、`/spark package` 根节点权限拆分、分页与路径复制。
6. **`SERVER_REMOTE` 的用途**：当前预留。若未来出现服务端接收远端内容的路径，再启用；否则可删除。
7. **`DETAIL` 的展示上限**：目前建议每模块 5 条、总共 10 行；实际作者反馈后再调。
8. **`SparkPackModule` 是否长期改为接收 `PackLoadContext`**：V1 保持签名不变；如果以后所有消费方都迁移到 Kotlin/新 API，再评估。
9. **是否重新引入去重**：V1 不做。若重复上报成为实际问题，使用调用方显式提供的语言无关 key，而不是 `message.string`。

---

## 10. 修订记录

| 版本 | 日期 | 内容 |
|------|------|------|
| 1.0 | 2026-10-18 | 初稿：数据模型、会话帧、错误通道、加载器接入、展示与分阶段计划 |
| 1.1 | 2026-10-18 | 按 2026-10-18 讨论修订：四值 `PackLoadContext`（端 + 来源）取代 `PackSide` 分桶；模块作为顶层分组；删除去重；移除 V1 命令，改为登录/重载摘要；修复 `/spark package reload` 的 `fromServer` 门控；明确模块契约与汇总边界；补 `@JvmOverloads` / `@JvmStatic`；修正 §2.3 现状描述；加入加载阶段事件 TODO |
| 1.2 | 2026-10-18 | 补充 §3.9 实施前检查：依赖排序失败跳过 `onFinish`、客户端通知走 common 事件、`beginSession` 顺序、日志与上限、测试隔离 |
| 1.3 | 2026-10-18 | 事件命名与契约：`PackAllFinishedEvent` 取代 `SparkPackSessionFinishedEvent`；P3 模块级事件定为 `PackModuleStartEvent` / `PackModuleFinishEvent`；V1 不做 per-file `read` 事件，必要时加元数据版 `PackFileReadEvent` 或每模块一次的 `PackModuleReadFinishedEvent` |
| 1.4 | 2026-10-18 | 实现 P0–P3：诊断数据模型/收集器/错误通道、加载器四值上下文接入、reload 修复、服务端汇总、客户端摘要与配置、三个加载阶段事件；补收集器/`PackCodec`/Java 互操作单元测试；记录实现差异与 P3 剩余项 |
