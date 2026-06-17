# 区块实体方块高程索引——设计与实施计划

> **状态**: 设计评审中 · **创建**: 2026-06-17 · **修订**: 2026-06-17\
> **目标版本**: Spark-Core 1.x / Machine-Max 1.0.3-beta.7\
> **涉及仓库**: Spark-Core（索引存储与维护）、Machine-Max（索引消费、弹道预测、区块强制加载）

***

## 目录

1. [背景与用途](#1-背景与用途)
2. [现存问题](#2-现存问题)
3. [意义](#3-意义)
4. [核心方案](#4-核心方案)
5. [数据结构设计](#5-数据结构设计)
6. [架构与整合](#6-架构与整合)
7. [API 设计](#7-api-设计)
8. [弹道预测与区块加载流程](#8-弹道预测与区块加载流程)
9. [实施计划](#9-实施计划)
10. [风险与对策](#10-风险与对策)
11. [不变部分清单](#11-不变部分清单)

***

## 1. 背景与用途

### 背景

Spark-Core 的物理系统通过 `PhysicsChunkManager` 将 Minecraft 区块中的方块转换为 Bullet 物理刚体（"地形刚体"），并按需激活加入物理世界。地形刚体的构建与激活由\*\*载具/玩家的物理包围盒（AABB）\*\*驱动：仅当物理体位于某区块附近时，该区块的地形刚体才会被激活。

Machine-Max 的数据驱动投射物系统（`ProjectileManager`）使用 JME `rayTest` 检测地形碰撞。投射物以 SoA 数组形式存储，在物理线程中执行半隐式 Euler 积分 + 射线碰撞检测。当投射物命中 `PhysicsChunkSection` 时，可破坏地形方块。

BallisticsFramework（另一个依赖项目）提供了完整的外部弹道计算 API（`RealisticTrajectory.forwardSolve()`），能够在开火时预先计算投射物的完整飞行轨迹（采样点列表：时间 + 位置 + 速度）。

### 用途

本计划所述的 **区块实体方块高程索引（ChunkHeightIndex）** 服务于以下目的：

1. **快速判定某区块的某 Y 区间是否存在实体方块**（有碰撞体积、非空气的方块），无需加载区块或构建地形刚体
2. **供投射物弹道预测使用**：判定弹道路径上的哪些区块确实有方块可被命中，只对需要命中判定的区块触发加载和地形构建
3. **作为投射物强制加载远程区块的决策依据**：投射物不受 Minecraft 区块加载制约（运行于物理线程），可以在任意位置飞行。当预测到弹道将命中未加载区块时，需要提前强制加载该区块并构建地形刚体

***

## 2. 现存问题

### 问题 1：高速投射物飞出地形激活范围导致穿模

当前 `PhysicsChunkManager.updateBuild()` 和 `updateActivation()` 的构建/激活范围仅覆盖距物理体（载具/玩家）2 个区块的范围。投射物速度可达 500+ m/s（每 tick 25+ 米），数 tick 内即可飞出激活范围。

**后果**：投射物飞行路径上的地形 section 未激活，`rayTest` 无法检测碰撞 → 投射物穿过山体/地面。

### 问题 2：无区块加载触发机制

当前系统没有任何逻辑根据投射物路径触发区块加载。对于未加载的区块（玩家从未到达过的区域），既没有 `LevelChunk` 可供方块状态读取，也没有 `PhysicsChunk` 供地形刚体构建。

**后果**：即使投射物命中了远程未加载区块中的地形，也无法进行碰撞判定（没有地形刚体存在于物理世界）。

### 问题 3：盲目全局激活的不可行性

若简单地让所有投射物无条件激活其所在区块的地形刚体，在战斗场景下（多投射物、多方向），可能触发数十个区块的地形构建。每个 section 的 `CompoundCollisionShape` 构建涉及 4096 个方块状态的 NBT 读取 + 洪水填充合并，开销不可忽略。

**后果**：性能压力不可控。

***

## 3. 意义

本方案解决了"**按需触发投射物路径上远程区块的加载与地形构建**"的核心问题，同时避免不必要的全量构建。

| 维度        | 价值                                                       |
| --------- | -------------------------------------------------------- |
| **功能完整性** | 投射物可命中玩家视野外的远程地形，实现真实的远程炮击/狙击体验                          |
| **性能可控**  | 通过高程索引精准过滤，只加载确实存在实体方块的区块，避免空气区间的无效构建                    |
| **计算可预测** | 借助 BallisticsFramework 在开火时预计算弹道，提前提交区块加载请求，避免弹丸到达时区块未就绪 |
| **存储轻量**  | 每区块仅需 2\~40 bytes（视方块分布复杂度），全部内存常驻                       |
| **职责清晰**  | 索引维护（Spark-Core）与索引消费（Machine-Max）分离，互不污染                |

***

## 4. 核心方案

### 4.1 设计原则

- **Per-chunk 实体方块 Y 区间列表**：不区分 x/z 列，只记录整个 chunk（16×256×16）内哪些 Y 范围存在实体方块
- **纯内存 + Level Attachment 永久持久化**：索引在内存中以 `ConcurrentHashMap<ChunkPos, ShortArray>` 维护。以 NeoForge `AttachmentType` 随 `ServerLevel` 持久化到存档。数据永不主动清理——即使区块卸载也保留，保证投射物能查询未加载区块的地形信息。以空间换时间
- **MC 区块加载时计算，方块变更时增量更新**：索引的生命周期与 MC 区块加载/卸载/变更一致，不依赖 `PhysicsChunkSection` 的存在
- **二分查询**：给定一个 Y 区间，O(log N) 判断是否与索引中任何区间相交

### 4.2 区间表达

采用 `ShortArray` 紧凑编码：`[min₁, max₁, min₂, max₂, ...]`，长度为偶数的 2N。

Short 范围 ±32767，覆盖 Minecraft 自定义维度的极限 Y 范围（约 -2032\~2032）。

**判定有方块的规则**：

- 使用 `state.hasCollision()`（Minecraft 1.21.1 内置方法，检查 `Block.hasCollision` 属性）
- 自动排除空气、水、熔岩等无碰撞体积的方块，无需传 null 给 IBlockReader
- `hasCollision()` 的结果可在 `BlockCollisionUtil.java` 中用 Block 级别 `ConcurrentHashMap` 缓存（与现有 `getPhysicsData()` 缓存模式一致）

**示例**：

```
主世界典型：[-64, 98, 120, 148]       → 2区间，8 bytes
下界空洞区：[-4, 36, 96, 127]         → 2区间，8 bytes  
下界全空洞：[]                         → 0区间，0 bytes（空 ShortArray）
末地大岛：  [35, 95]                   → 1区间，4 bytes
超平坦石头柱：[0, 255]                  → 1区间，4 bytes
```

### 4.3 查询方法

```kotlin
/**
 * 判断此区块在 [queryMinY, queryMaxY] 范围内是否存在实体方块
 */
fun ChunkHeightIndex.hasSolidInRange(chunkPos: ChunkPos, queryMinY: Short, queryMaxY: Short): Boolean {
    val intervals = chunkSolidIntervals[chunkPos] ?: return false  // 未加载 = 无数据
    var lo = 0; var hi = intervals.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val minY = intervals[mid and 0xFFFF_FFFE]  // 偶数索引
        val maxY = intervals[mid or 1]              // 奇数索引
        if (queryMaxY < minY) hi = mid - 2
        else if (queryMinY > maxY) lo = mid + 2
        else return true  // 有交集
    }
    return false
}
```

***

## 5. 数据结构设计

### 5.1 内存索引

```kotlin
/**
 * 区块实体方块高程索引。
 * 以 ChunkPos 为键，ShortArray 编码的 Y 区间列表为值。
 * 区间按 minY 升序排列，互不重叠。
 *
 * 内存常驻，随 MC 区块加载/卸载同步增删。
 * 线程安全：主线程写入（计算/更新），物理线程只读（查询）。
 */
class ChunkHeightIndex {
    /** ChunkPos.toLong() -> 升序排列的 Y区间 ShortArray [min1,max1, min2,max2, ...] */
    val intervals: ConcurrentHashMap<Long, ShortArray>
}
```

### 5.2 Level Attachment（持久化）

```kotlin
/**
 * 持久化在 ServerLevel 的 Attachment 上。
 * Map 的 key 为 ChunkPos.toLong()（packed x/z），value 为 ShortArray。
 *
 * 保存时机：LevelEvent.Save
 * 加载时机：PhysicsLevelInitEvent（服务端）
 */
val CHUNK_SOLID_INTERVALS: AttachmentType<MutableMap<Long, ShortArray>> = ...
```

### 5.3 区间合并算法

从 `LevelChunk` 构建区间时，对 24 个 section 依次扫描，合并相邻的含方块 section：

```kotlin
fun computeIntervals(chunk: LevelChunk, level: Level): ShortArray {
    val minSection = level.minSection
    val maxSection = level.maxSection
    val ranges = mutableListOf<Pair<Short, Short>>()

    val EMPTY_SENTINEL = (-1).toShort()  // 哨兵值，表示"当前无活跃区间"

    var rangeStart: Short = EMPTY_SENTINEL
    for (secY in minSection until maxSection) {
        val hasSolid = sectionHasSolidBlock(chunk, level, secY)
        if (hasSolid && rangeStart == EMPTY_SENTINEL) {
            rangeStart = (secY shl 4).toShort()  // section起始Y = secY * 16
        } else if (!hasSolid && rangeStart != EMPTY_SENTINEL) {
            ranges.add(rangeStart to ((secY shl 4) - 1).toShort())  // 上一section结束Y = secY*16-1
            rangeStart = EMPTY_SENTINEL
        }
    }
    if (rangeStart != EMPTY_SENTINEL) {
        ranges.add(rangeStart to ((maxSection shl 4) - 1).toShort())
    }

    return ShortArray(ranges.size * 2).also { arr ->
        ranges.forEachIndexed { i, (min, max) ->
            arr[i * 2] = min; arr[i * 2 + 1] = max
        }
    }
}

/**
 * 判断某个 section 内是否存在实体方块。
 *
 * 优化策略（两层过滤）：
 * 1. 调色板预检：若 section.hasOnlyAir() → 直接返回 false，跳过 4096 次遍历
 * 2. 对非纯空气 section，逐方块遍历，一旦找到 hasCollision() == true 即早期退出
 *
 * 对于下界空洞区、主世界高空/地下空洞区，第1层过滤可消除绝大部分空 section 的遍历开销。
 *
 * @param secY section 的世界 Y 坐标（如主世界 -4~19），非 0-based 内部索引
 */
private fun sectionHasSolidBlock(chunk: LevelChunk, level: Level, secY: Int): Boolean {
    // sectionY → 0-based 索引: secY - level.minSection
    // 例如主世界 minSection=-4，则 secY=-4 → index=0
    val sectionIndex = secY - level.minSection
    val section = chunk.getSection(sectionIndex)
    if (section.hasOnlyAir()) return false  // 调色板预检：纯空气 section 直接跳过

    val baseY = secY shl 4
    for (y in 0 until 16) {
        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val state = chunk.getBlockState(BlockPos(x, baseY + y, z))
                if (state.hasCollision()) return true  // 早期退出
            }
        }
    }
    return false
}
```

**性能**：

| 场景                       | 实际遍历量                                             |
| ------------------------ | ------------------------------------------------- |
| 纯空气 section（下界空洞、高空）     | `hasOnlyAir()` 检查 O(1) → 直接跳过，0 次 `getBlockState` |
| 有方块的 section（主世界地表）      | 调色板预检通过 → 通常在前几十次 `getBlockState` 中早期退出           |
| 最坏情况（全满 section，如超平坦石头柱） | 完整 4096 次遍历，但此类 section 极少                        |

首次进入新区块时，约 24 个 section 中通常 < 6 个需要完整遍历，其余由调色板预检或早期退出快速处理。

***

## 6. 架构与整合

### 6.1 分层职责

```
┌─────────────────────────────────────────────┐
│                  Machine-Max                  │
│  ┌───────────────────────────────────────┐  │
│  │ TrajectoryChunkPredictor               │  │
│  │  - BF forwardSolve() 预计算弹道        │  │
│  │  - 弹道 → 途经chunk列表 + Y范围        │  │
│  │  - 查询 ChunkHeightIndex 过滤          │  │
│  │  - 提交 chunk ticket 到主线程          │  │
│  └───────────────────────────────────────┘  │
│  ┌───────────────────────────────────────┐  │
│  │ ProjectileManager (修改)               │  │
│  │  - 开火时调用预测                      │  │
│  │  - 持有 pendingChunkTickets            │  │
│  │  - 飞行中监控/清理 ticket              │  │
│  └───────────────────────────────────────┘  │
└────────────────────┬────────────────────────┘
                     │ 查询                  提交ticket
                     ▼                        ▼
┌─────────────────────────────────────────────┐
│                  Spark-Core                  │
│  ┌───────────────────────────────────────┐  │
│  │ ChunkHeightIndex                       │  │
│  │  - intervals: ConcurrentHashMap        │  │
│  │  - hasSolidInRange(chunk, yMin, yMax)  │  │
│  │  - computeIntervals(chunk, level)      │  │
│  │  - updateSection(chunk, secY)          │  │
│  └───────────────────────────────────────┘  │
│  ┌───────────────────────────────────────┐  │
│  │ PhysicsChunkManager (修改)             │  │
│  │  - onChunkLoaded: 计算+存入索引        │  │
│  │  - onChunkUnloaded: 移除内存索引       │  │
│  │  - onBlockUpdated: 增量更新受影响section│  │
│  │  - getChunkHeightIndex() 只读访问      │  │
│  │  - saveToAttachment / loadFromAttachment│  │
│  └───────────────────────────────────────┘  │
│  ┌───────────────────────────────────────┐  │
│  │ SparkAttachments (Spark-Core)          │  │
│  │  - CHUNK_SOLID_INTERVALS              │  │
│  │  - AttachmentType 定义在 Spark-Core   │  │
│  └───────────────────────────────────────┘  │
│  ┌───────────────────────────────────────┐  │
│  │ SparkLevelKt (外观 API)               │  │
│  │  - Level.hasChunkSolidInRange()       │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

### 6.2 索引维护事件流

```
ChunkEvent.Load（主线程）
    └── PhysicsChunkManager.onChunkLoaded()
            ├── mcLoadedChunks[chunkPos] = chunk         ← 现有逻辑
            ├── computeIntervals(chunk, level)             ← 新增：计算区间
            ├── chunkHeightIndex.intervals[packedPos] = intervals  ← 新增：更新内存
            └── 标记 Attachment 脏（下次 save 写入）      ← 新增

方块变更（主线程）
    └── PhysicsChunkManager.onBlockUpdated(positions)
            ├── 标记 dirtySections                         ← 现有逻辑
            └── 对每个受影响的 sectionPos:
                    chunkHeightIndex.updateSection(chunkPos, sectionY, chunk)  ← 新增：增量重算

ChunkEvent.Unload（主线程）
    └── PhysicsChunkManager.onChunkUnloaded()
            ├── mcLoadedChunks.remove(chunkPos)            ← 现有逻辑
            ├── unloadPhysicsChunk(chunkPos)               ← 现有逻辑
            └── 内存索引保留不删，Attachment 数据保留不删 ← 为投射物远程查询保留数据

LevelEvent.Save（主线程）
    └── PhysicsChunkManager.saveToAttachment(serverLevel)
            └── serverLevel.setData(SparkAttachments.CHUNK_SOLID_INTERVALS, intervals)
                （全量保存，数据量极小无需增量）
```

### 6.3 线程安全

- **写入方**：仅主线程（ChunkEvent、方块变更、Save 事件均在主线程）
- **读取方**：物理线程（投射物查询） + 主线程（弹道预测可在物理线程，chunk ticket 提交回主线程）
- **数据容器**：`ConcurrentHashMap` + `ShortArray`（不可变，写入时替换整个数组引用）

***

## 7. API 设计

### 7.1 设计目标

索引数据的价值不限于投射物。任意模组都可能需要知道"某区块的某高度是否有方块"——天气系统判定降雪是否有遮挡、生成系统判断是否适合放置结构、AI 路径寻路判断障碍物。API 应向所有 Spark-Core 下游模组开放，不与 Machine-Max 耦合。

### 7.2 分层与访问级别

```
层级 3: 下游模组（Machine-Max 等）
        └── 通过 SparkLevelKt 扩展函数查询
        └── 不直接接触 ChunkHeightIndex / PhysicsChunkManager

层级 2: Spark-Core 公开 API（SparkLevelKt.kt）
        └── Level 扩展函数，封装跨模组访问

层级 1: Spark-Core 内部实现（ChunkHeightIndex）
        └── PhysicsChunkManager 内部持有，Spark-Core 内部使用
```

### 7.3 层级 2 — 公开查询 API

所有扩展方法定义在 `SparkLevelKt.kt`，基于 `Level` 的扩展属性/方法。

```kotlin
// ========== 基本查询 ==========

/**
 * 获取此 Level 的区块固体高度索引（若不存在则返回 null）。
 * 客户端始终返回 null（索引仅服务端维护）。
 */
val Level.chunkHeightIndex: ChunkHeightIndex?
    get() = (physicsLevel.terrainManager as? PhysicsChunkManager)?.getChunkHeightIndex()

/**
 * 查询指定区块位置的指定 Y 区间内是否存在实体方块。
 *
 * @param pos 任意方块坐标（自动转换为 ChunkPos）
 * @param yMin 查询区间的 Y 下界（MC 坐标）
 * @param yMax 查询区间的 Y 上界（MC 坐标）
 * @return true = 存在实体方块，false = 无数据或不存在
 *
 * 线程安全：可在任意线程调用（不访问 MC 世界状态）
 */
fun Level.hasSolidInRange(pos: BlockPos, yMin: Double, yMax: Double): Boolean {
    return chunkHeightIndex?.hasSolidInRange(
        ChunkPos(pos), yMin.toShort(), yMax.toShort()
    ) ?: false
}

// ========== 高级查询 ==========

/**
 * 查询指定区块的完整固体区间列表。
 *
 * @return 区间数组 [min1,max1, min2,max2, ...]，若无数据返回 null
 *         调用方**不得修改**返回的数组（共享引用，线程安全）
 */
fun Level.getSolidIntervals(chunkPos: ChunkPos): ShortArray? {
    return chunkHeightIndex?.getIntervals(chunkPos)
}

/**
 * 检查指定区块是否已建立索引（即曾被加载过）。
 * 可用于判断是否需要冷启动遍历。
 */
fun Level.hasChunkIndex(chunkPos: ChunkPos): Boolean {
    return chunkHeightIndex?.hasChunk(chunkPos) ?: false
}
```

### 7.4 层级 1 — ChunkHeightIndex 内部 API

```kotlin
class ChunkHeightIndex {
    // ========== 查询（物理线程 & 主线程安全） ==========

    /** 检查指定 chunk 在指定 Y 区间内是否有固体方块 */
    fun hasSolidInRange(chunkPos: ChunkPos, minY: Short, maxY: Short): Boolean

    /** 获取指定 chunk 的区间数组，无数据返回 null */
    fun getIntervals(chunkPos: ChunkPos): ShortArray?

    /** 检查指定 chunk 是否已有索引数据 */
    fun hasChunk(chunkPos: ChunkPos): Boolean

    /** 索引中已记录的 chunk 总数 */
    val size: Int

    // ========== 写入（仅主线程，由 PhysicsChunkManager 调用） ==========

    /** 计算并存储某 chunk 的区间 */
    fun computeAndPut(chunk: LevelChunk, level: Level)

    /** 增量更新某 chunk 的某 section 后重新合并区间 */
    fun updateSection(chunkPos: ChunkPos, chunk: LevelChunk, level: Level, sectionY: Int)

    /** 导出全部数据用于持久化 */
    fun toPersistentMap(): MutableMap<Long, ShortArray>

    /** 从持久化数据恢复 */
    fun loadFromPersistentMap(data: MutableMap<Long, ShortArray>)
}
```

### 7.5 使用示例

```kotlin
// === 投射物弹道预测（Machine-Max，可在物理线程） ===
fun checkTrajectoryCollision(level: Level, samples: List<TrajectorySample>): List<ChunkPos> {
    val chunksToLoad = mutableListOf<ChunkPos>()
    for (sample in samples) {
        val chunkPos = ChunkPos(BlockPos.containing(sample.pos.x, sample.pos.y, sample.pos.z))
        val y = sample.pos.y
        if (level.hasSolidInRange(BlockPos.containing(sample.pos), y - 2.0, y + 2.0)) {
            chunksToLoad.add(chunkPos)
        }
    }
    return chunksToLoad.distinct()
}

// === 天气系统降雪遮挡判定（任何模组，主线程） ===
fun isSnowBlockedByTerrain(level: Level, pos: BlockPos): Boolean {
    val chunkPos = ChunkPos(pos)
    // 查询 (当前位置Y, 天空) 是否有方块遮挡
    return level.hasSolidInRange(pos, pos.y.toDouble() + 1, level.maxBuildHeight.toDouble())
    // 若无数据（未探索区域），保守地认为无遮挡
}

// === 遍历所有已知固体区间（分析/调试用途） ===
fun analyzeChunk(level: Level, chunkPos: ChunkPos) {
    val intervals = level.getSolidIntervals(chunkPos) ?: return
    for (i in intervals.indices step 2) {
        println("  [${intervals[i]}, ${intervals[i + 1]}]")
    }
}
```

### 7.5 主动构建 API —— 预约挂载模式

查询索引只是第一步。当确认某 chunk 的某 Y 区间存在方块后，需要能**主动触发该 chunk 的 MC 区块加载 + 地形刚体构建 + 加入物理世界**。当前 `PhysicsChunkManager.updateBuild()`/`updateActivation()` 仅由载具 AABB 驱动，缺少外部"按需加载特定区块"的入口。

**设计原则：调用方只需说"什么时候需要、保持多久"，系统自动管理加载/释放。**

```kotlin
// ========== 主动构建（SparkLevelKt 公开外观） ==========

/**
 * 预约在指定延迟后加载指定区块的指定Y范围地形，并在保持一定 tick 后自动释放。
 *
 * 多次调度同一 chunk 会取所有调度中最早的 delayStart 和最晚的 expiry（自动合并）。
 * 调用方无需手动 release，系统在到期后自动释放。
 *
 * @param chunkPos 目标区块
 * @param yMin 需要的 Y 下界（仅激活覆盖此区间的 section）
 * @param yMax 需要的 Y 上界
 * @param delayTicks 延迟多少 tick 后开始加载（0 = 立即）。用于弹道预测的"提前加载"
 * @param holdTicks 加载就绪后保持多少 tick。逾时自动释放
 *
 * 调用线程：任意线程（内部自动投递到主线程处理 chunk 加载）
 *
 * 示例：
 *   // 投射物飞到时立即需要，保持 5 tick（约 0.25s）
 *   level.scheduleChunkLoad(chunk, yMin, yMax, delayTicks=0, holdTicks=5)
 *   // 弹道预测：3 tick 后才到，需要保持 2 tick
 *   level.scheduleChunkLoad(chunk, yMin, yMax, delayTicks=3, holdTicks=2)
 */
fun Level.scheduleChunkLoad(
    chunkPos: ChunkPos,
    yMin: Double,
    yMax: Double,
    delayTicks: Int,
    holdTicks: Int
)

/**
 * 检查指定区块的指定Y范围地形是否已就绪（已加载 + 已构建 + 已激活）。
 * 
 * @return true = 地形刚体已在物理世界中可用
 */
fun Level.isChunkTerrainReady(chunkPos: ChunkPos, yMin: Double, yMax: Double): Boolean

/**
 * 主动取消对某区块的预约（投射物销毁时调用）。
 * 若还有其他预约在等待此区块，不会实际释放。
 */
fun Level.cancelChunkLoad(chunkPos: ChunkPos)
```

**层级 1 内部对应方法**（`PhysicsChunkManager`）：

```kotlin
// ========== 主动构建（PhysicsChunkManager 内部） ==========

/** 内部调度条目 */
data class ChunkSchedule(
    val delayStartTick: Int,    // 绝对 tick（当前tick + delayTicks）
    val expireTick: Int,        // 绝对 tick（就绪tick + holdTicks）
    val yRange: IntRange        // section Y 范围
)

/**
 * 调度（或扩展）一个区块加载窗口。
 * 若已有调度，将 delayStart 取早、expire 取晚、yRange 取并集。
 */
fun scheduleTerrain(chunkPos: ChunkPos, yRange: IntRange, delayTicks: Int, holdTicks: Int)

/**
 * 取消某个调用方的调度请求。
 * 若该 chunk 仍有其他调度请求，不会释放。
 */
fun cancelSchedule(chunkPos: ChunkPos)
```

**使用示例（Machine-Max 投射物预测）**：

```kotlin
// 开火时：遍历弹道采样点，对每个需要碰撞判定的 chunk 预约加载窗口
fun scheduleChunkLoading(level: Level, trajectory: List<TrajectorySample>, currentTick: Int) {
    for (sample in trajectory) {
        val chunkPos = ChunkPos(BlockPos.containing(sample.pos.x, sample.pos.y, sample.pos.z))
        val y = sample.pos.y
        if (!level.isChunkTerrainReady(chunkPos, y - 3.0, y + 3.0)
            && level.hasSolidInRange(BlockPos.containing(sample.pos), y - 3.0, y + 3.0)) {

            // sample.time 是开火后的秒数，转换为 tick
            val delayTicks = (sample.time * 20).toInt() - currentTick
            level.scheduleChunkLoad(chunkPos, y - 3.0, y + 3.0,
                delayTicks.coerceAtLeast(0), holdTicks = 5)
        }
    }
}

// 投射物销毁
fun onProjectileDestroyed(level: Level, trajectory: List<TrajectorySample>) {
    // 可选：主动取消预约（系统也会在到期后自动释放，故非必须）
    trajectory.map { ChunkPos(BlockPos.containing(it.pos.x, it.pos.y, it.pos.z)) }
        .distinct()
        .forEach { level.cancelChunkLoad(it) }
}

// 飞行中检查 terrain 就绪状态（在 updatePointProjectiles 的暂停恢复逻辑中）
fun isTerrainReadyForProjectile(level: Level, pos: Vector3f): Boolean {
    return level.isChunkTerrainReady(
        ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z)),
        pos.y - 5.0, pos.y + 5.0
    )
}
```

#### 实现思路

**内部调度模型**：

```
PhysicsChunkManager 维护一个调度表:
  Map<ChunkPos, ChunkSchedule>

其中 ChunkSchedule 是合并后的结果:
  - delayStartTick: 所有请求中最早的开始时间
  - expireTick: 所有请求中最晚的过期时间
  - yRange: 所有请求 yRange 的并集

主线程每 tick 检查:
  for (schedule in schedules):
    if (currentTick >= schedule.delayStartTick && !已加载):
        → serverLevel.getChunk() + PhysicsChunk.load() + activateSections(yRange)
    if (currentTick >= schedule.expireTick):
        → 移除调度 → 若没有其他调度引用此chunk → removeTicket
```

**自动合并示例**：

```
投射物A: scheduleChunkLoad(C, yMin=50, yMax=60, delay=0, hold=10)
  → C: delayStart=T+0, expire=T+10, yRange=[50..60]

投射物B: scheduleChunkLoad(C, yMin=80, yMax=90, delay=3, hold=5)
  → C: delayStart=T+0, expire=MAX(T+10,T+8)=T+10, yRange=[50..60]∪[80..90]

投射物A destroy → cancelChunkLoad(C)
  → 内部标记移除A的请求 → 重新合并
  → C: delayStart=T+3, expire=T+8, yRange=[80..90]
  → 仍未过期，继续持有
```

**关键约束**：

- MC 区块加载（`getChunk`）必须在主线程；调度表的 tick 检查也在主线程
- 地形异步构建完成后通过 `BuildState` 轮询确认就绪
- 自动合并 = 调用方零心智负担；重叠窗口无需手动协调
- `cancelChunkLoad()` 是可选的——系统到期自动释放，手动取消用于节省资源

### 7.7 响应式 API（可选扩展）

对于需要在方块变更时得到通知的场景，可以提供监听器：

```kotlin
/**
 * 注册索引变更监听器（仅 Spark-Core 内部使用）。
 * 当某 chunk 的区间发生变化时回调（主线程）。
 */
@ApiStatus.Internal
fun ChunkHeightIndex.addListener(chunkPos: ChunkPos, listener: (ShortArray) -> Unit)
```

此项为可选扩展，Phase 1\~4 不实现，仅供未来使用方（如动态结构生成器）参考。

***

## 8. 弹道预测与区块加载流程

### 8.1 完整流程

```
                        [开火时刻 - 物理线程]
                                │
ProjectileManager.addProjectileInternal()
                                │
                    ┌───────────▼───────────┐
                    │ TrajectoryChunkPredictor │
                    │ .predict(startPos, vel,  │
                    │         projectileType)  │
                    └───────────┬───────────┘
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                   ▼
    BF forwardSolve()    射线遍历提取         Level.hasSolidInRange()
    → TrajectoryResult   途经chunk+Y范围      → 过滤只有方块的chunk
              │                 │                   │
              └─────────────────┴───────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │ 对每个需要碰撞的chunk:  │
                    │ Level.scheduleChunkLoad│  ← 预约: delayTicks=到达tick, holdTicks=5
                    │   (chunk, yMin, yMax,  │
                    │    delayTicks, holdTicks)│
                    └───────────┬───────────┘
                                │
                        [内部自动合并 & 管理]
              ┌─────────┼─────────┐
              ▼         ▼         ▼
         MC区块加载  地形构建   section激活
         (到delayTick  (async)   (加入物理世界)
          时触发)
                                │
                          [飞行中 - 每tick]
                                │
              Level.isChunkTerrainReady() == false?
                → 暂停投射物 1 tick 等待
              == true?
                → rayTest正常工作
                                │
                  [expireTick到达]
              → 系统自动释放ticket（无需手动）
```

### 8.2 弹道 → 途经chunk 提取

输入为 `TrajectoryResult`（`List<TrajectorySample>`），对相邻采样点做 3D DDA 网格遍历（chunk 分辨率 = 16 格步长）：

```
对每对相邻采样点 (s[i], s[i+1])：
  构造方向向量 d = s[i+1].pos - s[i].pos
  射线步进，步长为 chunk 边界跨度
  对每步进入的新 chunk：
    更新 ChunkVisitInfo:
      firstEnterTime = min(已有, s[i].time)
      yMin = min(已有, s[i].pos.y, s[i+1].pos.y)
      yMax = max(已有, s[i].pos.y, s[i+1].pos.y)
```

### 8.3 调度窗口生命周期

```java
/**
 * 投射物的区块加载调度由系统自动管理，无需手动跟踪。
 *
 * 开火时:
 *   TrajectoryChunkPredictor 遍历弹道采样点
 *   → level.scheduleChunkLoad(chunk, yMin, yMax, delayTicks, holdTicks)
 *   → 系统内部合并到 ChunkSchedule，自动判定开始/过期时机
 *
 * 飞行中:
 *   updatePointProjectiles 中检查 level.isChunkTerrainReady(chunk, yMin, yMax)
 *   → 未就绪 + 投射物接近 → 暂停 1 tick 等待
 *   → 就绪 → rayTest 正常执行
 *
 * 投射物销毁/超时:
 *   → 系统自动在 expireTick 释放 ticket（无需手动调用）
 *   → 可选：提前调用 level.cancelChunkLoad(chunk) 节省资源
 */
```

***

## 9. 实施计划

### Phase 1: Spark-Core — 基础设施【2-3 日】

| 任务   | 文件                                         | 说明                                                                                                                                                                                                                                       |
| ---- | ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0  | `util/BlockCollisionUtil.java`             | **新增** `hasCollision(BlockState)`: 使用 `state.hasCollision()`（Minecraft 1.21.1 `BlockStateBase` 内置方法，检查 `Block.hasCollision` 属性标志），无需传 `null` 给 `IBlockReader`/`BlockPos`，避免 fence/wall 等依赖上下文的方块上可能的 NPE。Block 级别 `ConcurrentHashMap` 缓存 |
| 1.1  | `physics/terrain/ChunkHeightIndex.kt` (新建) | 定义 `ChunkHeightIndex` 类：内存 `ConcurrentHashMap<Long, ShortArray>` + `hasSolidInRange()` + `computeIntervals()` + `updateSection()`                                                                                                        |
| 1.2  | `physics/terrain/ChunkHeightIndex.kt`      | 实现 `computeIntervals(chunk, level)`：先对各 section 做调色板预检（1.2a）→ 仅对非纯空气 section 做 4096 遍历 + 早期退出 → 合并相邻含方块 section → ShortArray                                                                                                             |
| 1.2a | `physics/terrain/ChunkHeightIndex.kt`      | 实现 `sectionHasSolidBlock()`：先检查 section 调色板，若 `section.hasOnlyAir()` 直接返回 false（避免空 section 的 4096 次全遍历）。非纯空气 section 再走 4096 遍历 + 早期退出                                                                                                  |
| 1.3  | `physics/terrain/ChunkHeightIndex.kt`      | 实现 `updateSection(chunk, level, sectionY)`：重算单 section，重新合并受影响的相邻区间                                                                                                                                                                      |

### Phase 2: Spark-Core — PhysicsChunkManager 集成 + 跨模组 API【1-2 日】

| 任务  | 文件                                       | 说明                                                                                                                                                           |
| --- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 2.1 | `physics/terrain/PhysicsChunkManager.kt` | 添加 `chunkHeightIndex: ChunkHeightIndex` 字段，构造函数初始化                                                                                                           |
| 2.2 | `physics/terrain/PhysicsChunkManager.kt` | `onChunkLoaded()` 中调用 `computeIntervals()` 写入内存索引                                                                                                            |
| 2.3 | `physics/terrain/PhysicsChunkManager.kt` | `onChunkUnloaded()` 中**保留**内存索引条目和 Attachment 数据（不删除）。投射物需要查询未加载区块的地形信息，空间换时间                                                                                |
| 2.4 | `physics/terrain/PhysicsChunkManager.kt` | `onBlockUpdated()` 中增量重算受影响 section 的区间                                                                                                                      |
| 2.5 | `physics/terrain/PhysicsChunkManager.kt` | 新增只读访问器 `fun getChunkHeightIndex(): ChunkHeightIndex`，供 Machine-Max 查询                                                                                       |
| 2.6 | `api/SparkLevelKt.kt`                    | 新增跨模组外观方法：`Level.hasSolidInRange()`、`Level.getSolidIntervals()`、`Level.hasChunkIndex()`、`Level.chunkHeightIndex`                                             |
| 2.7 | `physics/terrain/PhysicsChunkManager.kt` | 新增预约调度：`scheduleTerrain(chunkPos, yRange, delayTicks, holdTicks)` 和 `cancelSchedule(chunkPos)`。维护 `Map<ChunkPos, ChunkSchedule>` 调度表，每 tick 检查：到 delayStartTick 时触发 `getChunk` + `load()` + `activateSectionsInRanges()`；到 expireTick 时自动 `removeTicket`；支持多次调度的自动合并（取最早开始、最晚过期、Y范围并集） |
| 2.8 | `api/SparkLevelKt.kt` | 新增主动构建外观：`Level.scheduleChunkLoad(chunkPos, yMin, yMax, delayTicks, holdTicks)`、`Level.isChunkTerrainReady(chunkPos, yMin, yMax)`、`Level.cancelChunkLoad(chunkPos)` |

### Phase 3: Spark-Core — Attachment 持久化【1 日】

> **设计决策**：AttachmentType 定义在 Spark-Core 的 `SparkAttachments.kt`，而非 Machine-Max 的 `MMAttachments.kt`。
> 理由：索引数据由 Spark-Core 的 `PhysicsChunkManager` 全权维护（计算、更新、查询），持久化层应同属 Spark-Core。
> Machine-Max 仅通过 Phase 2.6 的公开 API 消费索引数据，不拥有 Attachment 所有权。

| 任务  | 文件                                       | 说明                                                                                                                                                                                                                      |
| --- | ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 3.1 | `registry/common/SparkAttachments.kt`    | 定义 `CHUNK_SOLID_INTERVALS: AttachmentType<MutableMap<Long, ShortArray>>`。`Long = ChunkPos.toLong()`                                                                                                                     |
| 3.2 | `physics/terrain/PhysicsChunkManager.kt` | 新增 `saveToAttachment(serverLevel)` / `loadFromAttachment(level)` 方法，封装 `setData`/`getData`                                                                                                                              |
| 3.3 | Spark-Core 内部事件监听                        | Spark-Core **自行监听** `LevelEvent.Save` 和 `PhysicsLevelInitEvent`——直接调用 `saveToAttachment()` / `loadFromAttachment()`。数据属于 Spark-Core，不应依赖 Machine-Max 的 `ObjectManager` 代理。若 Machine-Max 需要在 Save/Load 时机做额外逻辑，可自行监听相同事件 |

**跨模组 API 契约**（详见 [§7 API 设计](#7-api-设计)）：

```
Spark-Core 侧（生产者）:
  SparkAttachments.CHUNK_SOLID_INTERVALS       ← AttachmentType 定义
  PhysicsChunkManager.getChunkHeightIndex()    ← 内存索引访问
  PhysicsChunkManager.scheduleTerrain()        ← 预约调度（自动合并）
  PhysicsChunkManager.cancelSchedule()          ← 取消调度
  Level.chunkHeightIndex                        ← 查询外观
  Level.hasSolidInRange(...)                    ← 查询外观
  Level.scheduleChunkLoad(...)                  ← 预约外观
  Level.isChunkTerrainReady(...)                ← 就绪检查
  Level.cancelChunkLoad(...)                    ← 取消外观

Machine-Max 侧（消费者）:
  调用 Level.hasSolidInRange(...)               ← 查询
  调用 Level.scheduleChunkLoad(...)             ← 预约加载（含延迟和时长）
  调用 Level.isChunkTerrainReady(...)           ← 检查就绪
  调用 Level.cancelChunkLoad(...)               ← 提前释放（可选）
  不直接接触 PhysicsChunkManager / ChunkHeightIndex 内部类
```

### Phase 4: Machine-Max — 弹道预测与区块加载【3-4 日】

| 任务  | 文件                                                          | 说明                                                                             |
| --- | ----------------------------------------------------------- | ------------------------------------------------------------------------------ |
| 4.1 | `common/mech/projectile/TrajectoryChunkPredictor.java` (新建) | BF `forwardSolve()` 弹道预计算 → 途经 chunk 提取 → 索引过滤                                 |
| 4.2 | `common/mech/projectile/TrajectoryChunkPredictor.java`      | 实现 3D 射线遍历提取 chunk 列表 + Y 范围                                                   |
| 4.3 | `common/mech/projectile/ProjectileManager.java`             | `addProjectileInternal()` 中调用 `TrajectoryChunkPredictor` 生成弹道采样点，对每个 sample 调用 `level.scheduleChunkLoad(chunk, yMin, yMax, delayTicks, holdTicks)` 预约加载窗口。系统自动管理释放，无需在 Manager 中存储 ticket 列表 |
| 4.4 | `common/mech/projectile/ProjectileManager.java`             | `updatePointProjectiles()` 中新增暂停检查：`level.isChunkTerrainReady(chunk, yMin, yMax) == false` 且投射物已接近该 chunk → 暂停 1 tick 等待 |
| 4.5 | `common/mech/projectile/ProjectileManager.java`             | `swapRemove()` / 投射物销毁时可选调用 `level.cancelChunkLoad(chunk)` 提前释放 |

### Phase 5: 测试与调优【2-3 日】

| 任务  | 说明                                                              |
| --- | --------------------------------------------------------------- |
| 5.1 | 单元测试：`ChunkHeightIndex.computeIntervals()` 对各种维度（主世界/下界/末地/超平坦） |
| 5.2 | 单元测试：`hasSolidInRange()` 区间查询正确性（边界情况）                          |
| 5.3 | 单元测试：区间增量更新—方块破坏/放置后索引一致性                                       |
| 5.4 | 集成测试：投射物远程命中未加载区块中的地形                                           |
| 5.5 | 性能测试：大量投射物 + 大视距场景下的索引查询开销                                      |
| 5.6 | 持久化测试：存档加载后索引恢复正确性                                              |

***

## 10. 风险与对策

| 风险                         | 概率 | 影响                                                     | 对策                                                                                                                                      |
| -------------------------- | -- | ------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| **弹道预测与实际飞行的模型差异**         | 中  | 预测的途经 chunk 与实际有偏差                                     | BF 模型与 MM 自研积分器均使用二次阻力，偏差在 1\~2 chunk 内。加安全余量：Y 范围 ±2 格，chunk 半径 +1                                                                     |
| **区块加载延迟导致投射物飞过无碰撞**       | 中  | 远程命中漏判                                                 | 暂停机制：若未就绪 chunk 的 `enterTime < currentTime + 1 tick`，暂停投射物等待。作为 fallback，不做碰撞判定的丢失可接受（比穿模视觉效果好）                                         |
| **下界/末地等维度的特殊方块（水、熔岩、空气）** | 低  | 区间误判                                                   | 使用 `BlockCollisionUtil.hasCollision()` 而非简单 `isAir` 判定，液体不计入                                                                            |
| **模组自定义方块无碰撞形状但标记为固体**     | 低  | 多余加载                                                   | 可接受——最坏情况只是多加载了一个不会有碰撞的区块                                                                                                               |
| **极大量投射物（>100 发/帧）**       | 低  | ticket 管理压力                                            | 用引用计数合并同一 chunk 的多个 ticket；超出阈值时跳过弹道预测，fallback 到无预测模式                                                                                  |
| **区块从无固体变为有固体（玩家放置方块）**    | 低  | 索引更新                                                   | 方块变更事件已覆盖，`onBlockUpdated()` 中增量重算                                                                                                      |
| **Attachment 数据无界增长**      | 低  | 长期运行服务器探索大量区块后，Attachment Map 持续增长。100K 唯一区块 → \~3.2MB | **设计决策：空间换时间，永久保留。** 3.2MB 对现代服务器可忽略不计，而清理后丢失未加载区块的索引 → 投射物无法查询 → 需重建 = 更大的 IO 浪费。数据自然上界 = 玩家探索范围的区块总数（通常 < 50K），远低于危险阈值                |
| **ShortArray 写入可见性**       | 低  | 数组原地修改可能导致物理线程读到部分写入的数据                                | 实现约束：所有更新通过 `ConcurrentHashMap.put(key, newArray)` 替换整个数组引用，**严禁原地修改数组内容**后依赖 volatile/锁保证可见性。写入 = 创建新数组 → `put()`，读取 = `get()` 获取不可变引用 |

***

## 11. 不变部分清单

以下现有代码**不受本方案影响**，无需修改：

| 组件                                                         | 说明                          |
| ---------------------------------------------------------- | --------------------------- |
| `PhysicsChunkSection`                                      | 构建、激活、去激活逻辑不变               |
| `SectionSnapshot` / `BlockMerger`                          | 碰撞形状构建不变                    |
| `ProjectileManager.updatePointProjectiles()`               | 半隐式 Euler 积分 + rayTest 逻辑不变 |
| `ProjectileManager.clientExtrapolate()`                    | 客户端外推不变                     |
| `PointProjectile` / `RigidProjectile`                      | 投射物类本身不变                    |
| `ObjectManager` 现有生命周期方法                                   | 仅新增 Save/Load 钩子            |
| `PhysicsChunkManager.updateBuild()` / `updateActivation()` | 现有载具驱动逻辑不变                  |
| `BallisticsFramework` 全部 API                               | 仅作为依赖调用                     |

