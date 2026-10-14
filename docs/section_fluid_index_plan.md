# Section 流体索引 —— 设计与实施计划（Spark-Core 侧）

> **状态**: v2 已按评审修订；**Phase 1~3 + S0 已实现**（`./gradlew classes` 通过，未跑运行时验证）
> **创建**: 2026-10-14 · **修订**: 2026-10-14
> **目标版本**: Spark-Core 1.x / Machine-Max 1.0.3-beta.7
> **涉及仓库**: Spark-Core（索引存储与维护）、Machine-Max（浮力消费）
> **审阅意见**: [`section_fluid_index_plan_review.md`](./section_fluid_index_plan_review.md)

**相关文档**

- [`chunk_height_index_plan.md`](./chunk_height_index_plan.md) —— 本计划的直接前身，高程索引的
  「不可变实例 + 整体替换引用」约定、`DataMap` 机制、`AddReloadListenerEvent` 失效缓存均沿用于此。
- [`地形方块变更热路径优化建议.md`](./地形方块变更热路径优化建议.md) —— §2 给出了
  `setBlock → markAndNotifyBlock → onBlockStateChange → startAsyncUpdate` 的完整调用链与量级，
  本计划 §10.3 / §6.9 直接引用其结论，不重复论证。

**目标**：让物理线程能够廉价查询「某个世界坐标处是否有流体、是什么流体、液面在哪」，
为下游模组（Machine-Max）的水体浮力提供数据基础。
本计划**不引入任何刚体**，不参与碰撞检测，不产生任何接触流形。

***

## 目录

- [0. v1 → v2 修订摘要](#0-v1--v2-修订摘要)
- [1. 背景与目标](#1-背景与目标)
- [2. 核心决策：FluidState 是完全独立的通道](#2-核心决策fluidstate-是完全独立的通道)
- [3. 数据结构：256 列主布局 + 稀疏例外](#3-数据结构256-列主布局--稀疏例外)
- [4. 构建流程与线程模型](#4-构建流程与线程模型)
- [5. 查询 API](#5-查询-api)
- [6. 连锁改动清单（易踩坑）](#6-连锁改动清单易踩坑)
- [7. 流体物理属性：FluidPhysicsData](#7-流体物理属性fluidphysicsdata)
- [8. 实施计划](#8-实施计划)
- [9. 验证与基准](#9-验证与基准)
- [10. 已知限制与后续](#10-已知限制与后续)
- [11. 决策记录](#11-决策记录)

***

## 0. v1 → v2 修订摘要

针对 [`section_fluid_index_plan_review.md`](./section_fluid_index_plan_review.md) 的三条 P0 与六条 P1，
以及「用 `hasFluid` 而非 `isSolid` 判据、空气 voxel 双通道跳过」的补充意见，v2 做如下改动：

| 编号 | 审阅条目 | v2 处理 |
| --- | --- | --- |
| **P0-1** | 纯流体 section 的 `snapshotForCollision` 恒为 `null`，深水区浮力永远查不到 | **架构性修复**：流体索引不再经 `snapshotForCollision` 发布，改为 `PhysicsChunkSection` 上的独立 `@Volatile fluidIndexRef`，在**主线程** `prepareSnapshot()` / `startAsyncUpdate()` 中发布（[§4.4 / §6.4](#44-线程模型与发布通道)）。比评审建议的「`buildCollisionShape()` 早退分支补发布」更强：懒构建下从未激活的 section 也不会漏 |
| **P0-2** | `getHeight()` 语义与 §9 验收标准自相矛盾 | 采纳 **A 案（忠实对齐原版）**：顶层静水 `topQ == 14`（≈0.875），`topQ == 16` 仅在「上方有同类流体」时出现。§9 全文同步改写（[§3.4 / §9](#34-编码约定)） |
| **P0-3** | 批量查询放大三条既有隐患 | ① `PhysicsChunk.sections` 改为不可变视图、`loadedChunks` 改 `ConcurrentHashMap`（[§6.8](#68-并发容器改造p0-3)）；② batch 内部自缓存 chunk/section，不走 `getSectionForBlockPos`（[§5.4](#54-批量查询与-section-定位缓存p0-3)）；③ strict world→local 转换，**禁用** clamp 版 `getRelativePos()`（[§5.3](#53-单次查询路径)） |
| **P1-1** | 内存 9.5 KB 非 8.7 KB；「零开销」不成立；建议评估列布局 | 采纳列布局：**4096-voxel 布局改为 256 列主布局 + 稀疏例外**，≈1.2 KB/section（[§3](#3-数据结构256-列主布局--稀疏例外)） |
| **P1-2** | `colBottom` off-by-one 歧义 | 定义为「最低含水 voxel 的**底面**」，并补专项边界用例（[§3.4](#34-编码约定) / [§9](#9-验证与基准)） |
| **P1-3** | L5 未定义跨流体列 | 柱按 **`typeIdx` 相同**才连续（[§4.3](#43-列级索引构建)） |
| **P1-4** | fastutil map 被物理线程并发访问 | 改为 **volatile 不可变数组表**，`Fluid → id` 解析也移出物理线程（[§7](#7-流体物理属性fluidphysicsdata)） |
| **P1-5** | §10.3 担忧是反的；真问题是整节重建风暴 | 改写 §10.3 并新增 **流体-only 快路径**（[§6.9](#69-流体only-快路径p1-5)） |
| **P1-6** | 客户端不是待验证项 | 结论写死：客户端有 `terrainManager`、双端都收 `ChunkEvent`，但**无任何变更失效通知**，索引会永久过期（[§10.4](#104-客户端)） |
| **P2** | 错别字 / 漏引 / 链接 | 全文修（[§11](#11-决策记录) 逐条对应） |
| **补充** | 用 `hasFluid` 而非 `isSolid` 判据；空气 voxel 可跳过 | 双通道遍历以 `blockState.isAir` 整体早退；`hasSolid` / `hasFluid` 作为两个**正交**标志，流体通道只由 `hasFluid` 驱动（[§4.1](#41-挂载点与双通道遍历)） |

***

## 1. 背景与目标

### 1.1 现状：水对物理完全不可见

`SectionSnapshot.snapshotFromChunk()` 有**两个**早退（`SectionSnapshot.kt:43` / `:58`）：

```kotlin
// 早退 ①：section 级
if (section == null || section.hasOnlyAir()) return EMPTY          // :43

// 早退 ②：voxel 级（收录判据）
if (!blockState.isAir && !blockState.getCollisionShape(
        EmptyBlockGetter.INSTANCE, BlockPos.ZERO
    ).isEmpty
) { blockSnapshots[index] = BlockSnapshot(...) }                   // :58
```

水的碰撞形状为空，因此**从未被收录**，纯水 section 的快照恒为 `SectionSnapshot.EMPTY`，
`buildCollisionShape()` 直接返回 false，section 永不建立刚体。

> 早退 ① 对本计划**是安全的**：`LevelChunkSection.hasOnlyAir()` 判据是 `nonEmptyBlockCount == 0`，
> 而水本身是一个 Block（非空 blockstate），纯水 section 不会被它筛掉。（v1 漏提，补齐。）

### 1.2 目标

- 物理线程可查询：给定世界浮点坐标，得到**流体类型 + 该点所在流体柱的液面世界 Y**。
  复杂度：**最坏 O(1)**；仅当该列存在多层水且采样点落在非最高柱时，退化为对稀疏例外表的
  O(log n) 二分（开销正比于该列的水体段数，实际 ≤ 3）。
- 零物理开销：不进 broadphase、不生成流形、不参与求解。
- 内存可控：仅在真正含流体的 section 上分配，且按多级标志逐级短路。
- 可扩展：流体物理属性（密度、粘度）走 DataMap，允许下游模组注册自定义流体。

### 1.3 非目标

- 不做浮力计算本身（下游模组负责）。
- 不做流体模拟 / 流动传播。
- 不建模流体对碰撞的影响（水不阻挡、不产生接触）。
- 不处理流体渲染。

***

## 2. 核心决策：FluidState 是完全独立的通道

### 2.1 为什么不能挂在 `BlockSnapshot` 上

`BlockSnapshot` 的构造前提是「该方块有碰撞体积」，其字段（friction / rollingFriction /
restitution / slip）全部是**接触力学**语义。流体的语义完全不同：

| 维度 | BlockSnapshot | 流体 |
| --- | --- | --- |
| 存在判据 | 有碰撞形状 | `FluidState` 非空 |
| 关键量 | 摩擦/弹性/湿滑 | 密度/粘度/液面高度 |
| 几何 | 由 BlockMerger 合并为盒 | 每 voxel 一个液面高度 |
| 用途 | 接触求解 | 浸没体积积分 |

**含水方块（waterlogged）是决定性的反例**：

| 方块 | 有碰撞 | 含水 |
| --- | --- | --- |
| `Blocks.WATER` | ✗ | ✓ |
| `Blocks.KELP` / `Blocks.SEAGRASS` | ✗ | ✓ |
| `OakSlabBlock`（waterlogged=true） | ✓ | ✓ |

第三类说明「含水」与「有碰撞」**完全正交**；第二类说明即使放宽 `hasCollision` 判据也覆盖不全。

> **判据结论（补充意见采纳）**：流体通道的判据是 `hasFluid`（= `!fluidState.isEmpty`），
> 与 `isSolid`（= 有非空碰撞形状）**互不可推导**。遍历时必须把二者写成两个独立的 `if`，
> 不允许写成 `if (isSolid) {...} else if (hasFluid) {...}` 之类的互斥分支。

### 2.2 为什么仍放在 `SectionSnapshot` 里

两者同源于同一次 4096-voxel 遍历、同一次区块快照、同一套生命周期（构建 / 替换 / 销毁）。
拆成两个并行结构会引入双份生命周期管理和双份线程安全约定。

**结论**：`SectionSnapshot` 增加一个与 `blockSnapshots` **平级**的字段 `fluidIndex`，
二者在数据上零耦合，仅共享生命周期容器。

### 2.3 通道独立性带来的语义清单

明确以下不变量，避免后续误用：

1. `blockSnapshots[i] == null` **不蕴含**该 voxel 无流体。
2. `blockSnapshots[i] != null` **不蕴含**该 voxel 无流体（含水方块两者皆有）。
3. 流体索引**不参与** `markRemoved()` / `isRemoved()` —— 那是固体破坏追踪。
4. 流体索引**不影响** `collisionShape` 的构建，`BlockMerger` 完全不感知流体。
5. `SectionSnapshot.EMPTY` 的语义收紧为「无固体**且**无流体」。
6. 空气 voxel（`BlockState.isAir`）**双通道皆不可能命中**，遍历时整体跳过（[§4.1](#41-挂载点与双通道遍历)）。

***

## 3. 数据结构：256 列主布局 + 稀疏例外

> **变更（P1-1）**：v1 的「4096 voxel 位图 + 逐 voxel `topQ`/`typeIdx`」为**最坏情况付费**——
> 哪怕只有一滴水也要付全套 9.5 KB。而 L3/L4 的逐 voxel 精度只在「同一列有多层水」这种
> 罕见情形下才有意义；绝大多数用途（含 Machine-Max 的静力点）只需要「某一列的最高水柱」。
> v2 改为**按列存储**，≈1.2 KB/section，7~8 倍收益。

### 3.1 分级与短路路径

| 级别 | 粒度 | 载体 | 回答的问题 | 代价 |
| --- | --- | --- | --- | --- |
| L0 | section | `fluidIndex != null` | 这个 section 有没有液体 | 1 次空判 |
| L1 | y 层（16 个） | `layerMask: Int` | 这个 y 层有没有液体 | 1 次位测试 |
| L2 | 列（256 个） | `colTopY/colBottomY/colTopQ/colType` | 该列最高水柱的顶/底/液面/类型 | 4 次数组读 |
| L3 | 列（256 个） | `colReachesTopBits: LongArray(4)` | 该柱是否续接到上方 section | 1 次位测试 |
| L4 | 例外列 | 5 条平行扁平数组 | 非最高柱（悬空水洼）的段 | 1 次二分 |
| L5 | 类型 | `palette: IntArray` | 是什么液体 | 1 次数组读 |

L0 为 false 时 L1~L5 **全部不分配**（`fluidIndex == null`）。

### 3.2 `SectionFluidIndex` 定义

```kotlin
package cn.solarmoon.spark_core.physics.terrain

/**
 * Section 级流体索引。
 *
 * 完全独立于 [SectionSnapshot.BlockSnapshot]：流体存在判据是 FluidState 非空，
 * 与方块是否有碰撞体积正交（含水方块二者皆有，海带/海草只具后者）。
 *
 * 实例不可变：构建完成后只读，替换整个引用以发布到物理线程（与 ChunkHeightIndex 同约定）。
 * 物理线程只读，主线程发布；所有数组构建后禁止原地修改。
 */
class SectionFluidIndex private constructor(
    /** L1：16 个 y 层的流体存在位掩码，bit i = 局部 y=i 层是否有流体 */
    val layerMask: Int,

    // ---- L2：每列「最高柱」----
    /** 最高柱柱顶 voxel 的局部 y（0..15）；255 = 该列无流体 */
    val colTopY: ByteArray,
    /** 最高柱**最低含水 voxel** 的局部 y（0..15）—— 见 §3.4 的柱底定义 */
    val colBottomY: ByteArray,
    /** 最高柱顶面相对柱顶 voxel 底面的高度，1/16 块精度，取值 1..16；0 = 该列无流体 */
    val colTopQ: ByteArray,
    /** 最高柱的 fluid palette 下标 + 1；0 = 该列无流体 */
    val colType: ByteArray,

    /** L3：bit c = 第 c 列的最高柱是否续接到上方 section（跨 section 续接标记） */
    val colReachesTopBits: LongArray,   // LongArray(4) = 256 bit

    /** L5：section 内出现的流体全局 id（BuiltInRegistries.FLUID），下标 0 保留给「无」 */
    val palette: IntArray,

    // ---- L4：稀疏例外（该列存在第二及以下柱）----
    /** 例外列的列下标（0..255），升序；null = 无例外 */
    val excColIdx: ByteArray?,
    /** 与 [excColIdx] 对齐：每段的起始偏移在下方扁平数组中的起点 */
    val excSegStart: IntArray?,
    /** 扁平：段的最低含水 voxel 局部 y */
    val excBottomY: ByteArray?,
    /** 扁平：段顶 voxel 局部 y */
    val excTopY: ByteArray?,
    /** 扁平：段顶面 1/16 量化高度 */
    val excTopQ: ByteArray?,
    /** 扁平：段的 palette 下标 + 1 */
    val excType: ByteArray?
) {
    /** 列索引公式：与 SectionSnapshot.getRelativePosFromIndex 的 x/z 平面保持一致 */
    inline fun colIndex(lx: Int, lz: Int): Int = lx + (lz shl 4)

    companion object {
        const val Q = 16                 // 1/16 块精度
        const val NO_COLUMN: Byte = (-1).toByte()
    }
}
```

### 3.3 内存核算（修正 P1-1）

仅统计**含流体**的 section：

| 字段 | 尺寸 |
| --- | --- |
| `layerMask` | 4 B |
| `colTopY` + `colBottomY` + `colTopQ` + `colType` | 4 × 256 B = 1024 B |
| `colReachesTopBits` | LongArray(4) = 32 B |
| `palette` | 通常 1~2 项，≤ 32 B |
| 例外数组（多数 section 为 null） | 0 B |
| 对象头 + 数组头 | ≈ 100 B |
| **合计（无例外）** | **≈ 1.2 KB / section** |
| 每条例外段 | 5 B |

对比 v1 的 9.5 KB，收益 **≈ 7.8×**。

**海洋世界实测量级估算**（v1 的「零开销」结论作废）：

```text
每 section        ~1.2 KB
每海洋 chunk      ~8 个整节满水 section × 1.2 KB ≈ 9.6 KB
300 个已加载 chunk（半数含水面）  ≈ 150 × 9.6 KB ≈ 1.4 MB 常驻
```

结论改写为：**陆地 / 地下 / 高空 section 零开销；含流体 section 每节约 1.2 KB**。
1.4 MB 常驻可接受，无需降级；Phase 4 仍需在 §9 实测「含流体 section 的分布」后再确认。

### 3.4 编码约定

**① 柱底定义（修正 P1-2 off-by-one）**

设某列最高柱的最低含水 voxel 局部 y 为 `yb`、柱顶 voxel 局部 y 为 `yt`、
section 最小世界 Y 为 `y0 = sectionPos.minBlockY()`：

```text
柱底世界 Y = y0 + yb            ← 该 voxel 的【底面】，不是它下方那一格
柱顶世界 Y = y0 + yt + topQ/16  ← 该 voxel 内部的【液面】
```

判据：`sampleY ∈ [y0 + yb, y0 + yt + topQ/16)` 视为浸没。
**不得**把「最低含水 voxel 的下面那一格」当作柱底——那会让水底凭空多出一格浮力。

**② 高度量化（采纳 P0-2 的 A 案：忠实对齐原版）**

```kotlin
topQ[i] = round(fluidHeight * 16).coerceIn(1, 16)   // 0 保留为「无流体」
```

MC 1.21.1 源码事实（`FlowingFluid.java:473-480`、`WaterFluid.java:137-151`）：

```java
float getHeight(FluidState st, BlockGetter lv, BlockPos p) {
    return hasSameAbove(st, lv, p) ? 1.0F : st.getOwnHeight();   // 8/9 ≈ 0.8889 for water source
}
float getOwnHeight(FluidState st) { return st.getAmount() / 9.0F; }
// WaterFluid.Source#getAmount → 8
```

由此推出**必须写进 §9 的三条结论**：

| 情形 | `getHeight()` | `topQ` | 液面世界 Y |
| --- | --- | --- | --- |
| 顶层静水（上方是空气） | 8/9 ≈ 0.8889 | **14** | `voxelBottom + 0.875` |
| 水体内部（上方同类流体） | 1.0 | **16** | `voxelBottom + 1.0` |
| 流水（LEVEL > 0，上方空气） | amount/9 | `round(amount/9×16)` | 按量化值 |

即 **`topQ == 16` 只在「上方还有同类流体」时出现**。v1 §9 写的「waterlogged slab → 满格 16」
是错的，已按本表改写。

量化本身误差 ≤ 1/32 格（0.031）；叠加原版自由液面本身在 8/9 后，静水自由液面的
**绝对误差是 0.875 vs 0.8889 = 0.0139 格（1.4 cm）**，对浮力积分无感，但必须在 §10 写明，
避免下游误以为拿到了精确液面。

> 若下游希望「格子对齐」的液面，不要在 Spark-Core 内置偏差——改为在 `FluidPhysicsData`
> 里加一个 `surfaceBias`（默认 0），由下游自行配置（[§7](#7-流体物理属性fluidphysicsdata)）。

**③ 为什么不用 `getAmount()`**

`getAmount()`（1..8）刻度是 1/9，对原版水最精确，但 mod 自定义流体的高度未必落在 1/9 刻度上。
用 1/16 量化保持跨模组通用，且 1.4 cm 的误差可接受。

**④ 同流体才连续（修正 P1-3）**

「柱」的定义要求段内所有 voxel 的 `typeIdx` **相同**。底层熔岩 + 上层水不是一柱，而是两段，
上面的水进 L2 主柱、下面的熔岩进 L4 例外表。这样 `sampleFluid` 在熔岩内返回
`fluidId = 熔岩` 且 `surfaceY` 是**熔岩段**的顶面，不会把整段水+岩浆都算成熔岩排开体积。

**⑤ `colReachesTop` 判据（修正 P1-2 附带的连带错误）**

> 柱顶 voxel 局部 y == 15 **且上方下一 section 的最低格仍是同类流体**。

**不得**用 `topQ == 16` 判定——按 ② 的取值法，静水顶层 `topQ` 是 14，
用 `topQ == 16` 会导致该标记永远为 false，§10.1 的跨 section 逻辑永远走不到。

***

## 4. 构建流程与线程模型

### 4.1 挂载点与双通道遍历

构建发生在**主线程**的 `SectionSnapshot.snapshotFromChunk()`，与现有 4096 遍历**同一次循环**，
不增加遍历次数：

```kotlin
for (x in 0 until 16) {
    for (y in 0 until 16) {
        for (z in 0 until 16) {
            val blockState = section.getBlockState(x, y, z)

            // ★ 空气 voxel：双通道都不可能命中，整体跳过
            //   - 固体通道：空气无碰撞形状
            //   - 流体通道：Block#getFluidState 对 air / cave_air / void_air 恒返回 Fluids.EMPTY
            //   （BlockState#isAir 对上述三者均为 true）
            if (blockState.isAir) continue

            // ---- 通道 A：固体（既有逻辑，完全不变） ----
            if (!blockState.getCollisionShape(
                    EmptyBlockGetter.INSTANCE, BlockPos.ZERO
                ).isEmpty
            ) {
                val worldPos = getWorldPos(pos, x, y, z)
                val physicsData = BlockCollisionUtil.getPhysicsData(blockState)
                blockSnapshots[index] = BlockSnapshot(
                    blockState,
                    physicsData.friction, physicsData.rollingFriction, physicsData.restitution,
                    BlockCollisionUtil.calcSlip(physicsData, chunk, worldPos)
                )
                hasSolid = true
            }

            // ---- 通道 B：流体（新增，与 A 正交） ----
            // 判据是 FluidState 自身，不是 A 的结果：
            //   含水方块 A、B 同时命中；海带/海草只命中 B；干石头只命中 A。
            val fluidState = blockState.getFluidState()      // 无分配：非流体返回静态空实例
            if (!fluidState.isEmpty) {
                fluidBuilder.mark(x, y, z, fluidState, blockState, section)
                hasFluid = true                             // ← 流体通道只由 hasFluid 驱动
            }
        }
    }
}
```

两个布尔标志 `hasSolid` / `hasFluid` **正交**，最终 EMPTY 判据：

```kotlin
return if (hasSolid || hasFluid)
    SectionSnapshot(blockSnapshots, fluidBuilder.build(), pos, hasSolid, hasFluid)
else EMPTY
```

### 4.2 高度计算（含无分配优化）

`fluidState.getHeight(level, worldPos)` 内部会 `pos.above()`，每次调用分配一个 `BlockPos`。
对 15/16 的层可以完全绕开：

```kotlin
val h: Float = if (y < 15) {
    // 上方 voxel 在本 section 内：直接读 PalettedContainer，零分配
    val above = section.getBlockState(x, y + 1, z).getFluidState()
    if (fluidState.type.isSame(above.type)) 1.0f else fluidState.ownHeight
} else {
    // 局部 y == 15：跨 section，走原版 BlockGetter（每 section 最多 256 次）
    fluidState.getHeight(level, getWorldPos(pos, x, y, z))
}
```

`isSame` 与 `hasSameAbove`（`FlowingFluid.java:468-470`）的语义**逐字一致**，故等价。
`section.getBlockState(...)` 与 `blockState.getFluidState()` 均不分配对象。

> 提醒：`LevelChunk.getFluidState` 内部是 try/catch + CrashReport + 坐标 `& 15` 掩码，
> **不要把任何跨区块坐标喂进去**。局部 y == 15 时传的 `worldPos` 是合法世界坐标，`above()`
> 仍在同一 chunk 列内，安全。

### 4.3 列级索引构建

每列 `(x, z)` 从局部 y = 15 向下扫描一次，把该列切成若干**同流体的连续段**：

1. 自上而下，按 `typeIdx` 相同才连续的原则切段；
2. 记录每段的 `(bottomY, topY, topQ, typeIdx)`，其中
   - `bottomY` = 该段**最低含水 voxel** 的局部 y（柱底 = 它的底面，见 §3.4①）；
   - `topY` = 柱顶 voxel 的局部 y；
   - `topQ` = 柱顶 voxel 的液面量化高度；
3. **最高的一段**写入 L2 主数组（`colTopY` / `colBottomY` / `colTopQ` / `colType`）；
4. **其余段**写入 L4 稀疏例外表的扁平数组，并记录 `excColIdx` / `excSegStart`；
5. 若最高段的 `topY == 15` 且上一 section 同列最低格为同类流体，置 `colReachesTopBits` 对应位。

`palette` 用 `ArrayList<Fluid>` 在遍历期线性查找去重（通常 ≤ 4 项），
遍历结束后一次性解析为 `BuiltInRegistries.FLUID` 的 id——
**避免每 voxel 一次注册表查询**。

### 4.4 线程模型与发布通道

> **这里是 v2 相对 v1 最重要的架构改动，用于根治 P0-1。**

#### v1 的致命耦合

v1 的 `getFluidIndex() = snapshotForCollision?.fluidIndex`，而 `snapshotForCollision`
**只有在刚体构建成功时才会被发布**：

- `replaceCollisionSnapshot()` 只有两个调用点：`createPhysicsBody()`(`PhysicsChunkSection.kt:277`)
  与 `startAsyncUpdate()`(`:246`)；
- `createPhysicsBody()` 只在 `buildCollisionShape()` 返回 `true` 后被调用；
- 而 §6.2 恰恰要求纯水 section 在 `!hasSolid` 时 return false。

于是纯水 section 的 `snapshotForCollision` **恒为 null** —— 水越深越查不到。
v1 §6.7 的推导读的是 `snapshotForBuild`，与 §6.4 的实现互相打架。

#### v2：流体索引走独立发布通道

**数据**仍在 `SectionSnapshot`（同一次遍历、同一 EMPTY 语义），
但**发布通道独立**，不复用 `snapshotForCollision`：

```kotlin
class PhysicsChunkSection(...) {
    /**
     * 流体索引的发布通道。
     *
     * 为什么不由 snapshotForCollision 携带：后者只在 buildCollisionShape() 成功后
     * 由 createPhysicsBody() 发布，而纯流体 section 永远 build 失败（无刚体），
     * 会导致深水区浮力永远查不到（P0-1）。另外 snapshotForBuild 没有 @Volatile，
     * 不能让物理线程直接读它。
     *
     * 主线程写、物理线程读；实例不可变，故单个 volatile 写即可安全发布。
     */
    @Volatile
    private var fluidIndexRef: SectionFluidIndex? = null

    /** 物理线程调用。【不得】加 isEmpty() 守卫，原因见 §6.4 */
    fun getFluidIndex(): SectionFluidIndex? = fluidIndexRef

    fun prepareSnapshot() {
        if (snapshotForBuild == null) {
            snapshotForBuild = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
            publishFluidIndex()
        }
    }

    fun startAsyncUpdate(manager: PhysicsChunkManager) {
        buildJob?.cancel()
        snapshotForBuild = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
        publishFluidIndex()          // ← 新增：主线程同步发布，物理线程立即可见
        ...
    }

    private fun publishFluidIndex() {
        fluidIndexRef = snapshotForBuild?.fluidIndex
    }

    fun destroy() {
        ...
        fluidIndexRef = null
    }
}
```

**为什么这比「在 `buildCollisionShape()` 的早退分支补一次 `replaceCollisionSnapshot()`」更强**：

| 场景 | v1 + 评审建议修法 | v2 独立通道 |
| --- | --- | --- |
| 纯水 section，已激活 | ✓（早退分支补发布） | ✓ |
| 纯水 section，**从未激活**（懒构建，`buildCollisionShape` 根本没跑过） | ✗ **仍为 null** | ✓ |
| 由水变干后再灌水 | 需额外在 `startAsyncUpdate` 的 `else` 分支补一次 | ✓ 天然覆盖 |
| 额外代价 | 每次 `copy()` 克隆 4096 引用 | 0（不 clone） |

`prepareSnapshot()` 由 `PhysicsChunk.load()`(`PhysicsChunk.kt:50`) 对**所有** sectionY 调用，
因此流体索引在区块加载后、且在**任何激活之前**即可用。这一条直接把 §6.7 的风险从
「该 section 必须被激活」降级为「该 chunk 必须在 `loadedChunks` 里」。

沿用 `ChunkHeightIndex` 的既有约定：

- **主线程**：构建 `SectionFluidIndex` → `publishFluidIndex()`（单一 volatile 写）；
- **物理线程**：只读 `getFluidIndex()`，不解引用任何可变状态；
- 实例**不可变**：严禁原地修改数组内容（与 `refreshSlipIfNeeded` 修改 `blockSnapshots` 的做法不同，
  流体索引不参与湿滑刷新）。

***

## 5. 查询 API

### 5.1 分层与访问级别

| 级别 | 位置 | 使用者 |
| --- | --- | --- |
| 内部 | `SectionFluidIndex` | `PhysicsChunkSection` |
| section 级 | `PhysicsChunkSection.getFluidIndex()` | `PhysicsChunkManager` |
| 公开查询 | `PhysicsChunkManager` / `PhysicsLevel` | 下游模组（物理线程） |

### 5.2 核心 API

```kotlin
// ---- PhysicsChunkSection（绕过 isEmpty() 守卫，见 §6.4）----
fun getFluidIndex(): SectionFluidIndex?

// ---- PhysicsChunkManager ----
/** 该点是否浸没在流体中 */
fun isSubmerged(x: Double, y: Double, z: Double): Boolean

/** 采样某点的流体状态。@return null = 该点无流体（含 section 未加载 / 无流体索引） */
fun sampleFluid(x: Double, y: Double, z: Double): FluidSample?

/**
 * 批量采样（推荐路径）。内部缓存上一次命中的 chunk / section，
 * 不经过 getSectionForBlockPos（避免每次分配 ChunkPos），见 §5.4。
 *
 * @param points  [x0,y0,z0, x1,y1,z1, ...]
 * @param outIds  输出 fluidId，-1 = 无
 * @param outSy   输出液面世界 Y，无则 Float.NEGATIVE_INFINITY
 * @return 命中数量
 */
fun sampleFluidBatch(points: DoubleArray, outIds: IntArray, outSy: FloatArray): Int

data class FluidSample(
    /** 全局 fluid id（BuiltInRegistries.FLUID），供下游查 FluidPhysicsData */
    val fluidId: Int,
    /** 该点所在流体柱的液面世界 Y */
    val surfaceY: Float,
    /** 浸没深度 = surfaceY - y，恒 > 0 */
    val depth: Float
)
```

### 5.3 单次查询路径

```text
sampleFluid(x, y, z)
 ├─ L0  section 定位（chunk + sectionY）失败                → null
 ├─ L0  section.getFluidIndex() == null                     → null
 ├─ strict local 转换：lx/ly/lz 不在 0..15                  → 抛错/返回 null（见下）
 ├─ L1  layerMask and (1 shl ly) == 0                       → null
 ├─ L2  c = lx + (lz shl 4)
 │      colTopQ[c] == 0                                     → null   （该列无流体）
 │      ly > colTopY[c]                                     → null   （在最高柱之上）
 │      ly == colTopY[c] && (y - (y0+ly)) >= topQ/16f       → null   （在液面之上）
 │      ly >= colBottomY[c]                                 → 命中主柱 ✓ O(1)
 └─ L4  ly < colBottomY[c] → 二分 excColIdx 找段             → 命中/未命中 ✓ O(log n)
```

> **strict 转换（P0-3 ③）**：**禁止**使用 `SectionSnapshot.getRelativePos()`
> （`SectionSnapshot.kt:140-145`），它是 `max(min(v - origin, 15), 0)` 的**夹取**语义。
> 采样点落在相邻 chunk / 相邻 section 时会被静默夹到边界格，返回**另一个格子**的水面高度，
> 浮力会突然与视觉不符。必须自己实现：
>
> ```kotlin
> // 返回 -1 表示越界。越界 ≠「无水」，而是「查询请求无效」，调用方应重新定位 section。
> inline fun localIndex(wx: Double, wy: Double, wz: Double, originX: Int, originY: Int, originZ: Int): Int
> ```
>
> 并且 `sampleFluid` 里要先用 `floor(y) shr 4` 判定 `sectionY == expected`，不匹配直接换 section。

### 5.4 批量查询与 section 定位缓存（P0-3 ②）

`PhysicsChunkManager.getSectionForBlockPos()`（`:374-377`）每次分配一个 `ChunkPos`：

```kotlin
fun getSectionForBlockPos(blockPos: BlockPos): PhysicsChunkSection? {
    val chunkPos = ChunkPos(blockPos)          // ← 每次分配
    return loadedChunks[chunkPos]?.getSectionForBlockPos(blockPos)
}
```

一批 64 个静力点就是 64 次 `ChunkPos` 分配 + 64 次 HashMap 查找。
`sampleFluidBatch` **不得**套用这个 helper，而是在 batch 内部自己缓存：

```kotlin
// batch 内部状态
var lastChunkKey = Long.MIN_VALUE            // ChunkPos.asLong() = (cx & 0xFFFFFFFFL) shl 32 or (cz & ...)
var lastChunk: PhysicsChunk? = null
var lastSectionY = Int.MIN_VALUE
var lastSection: PhysicsChunkSection? = null
```

船的静力采样点是一团连续偏移，绝大多数点落在同一 chunk 同一 section 上，命中率极高。
配套在 `PhysicsChunkManager` 上加一个**零分配**的直接访问器供 batch 使用：

```kotlin
fun getSectionByCoords(chunkX: Int, chunkZ: Int, sectionY: Int): PhysicsChunkSection?
```

### 5.5 下游使用示例（Machine-Max 静力点）

```kotlin
val hits = physicsLevel.terrainManager.sampleFluidBatch(points, ids, surfaces)
for (i in 0 until points.size / 3) {
    val id = ids[i]
    if (id < 0) continue                       // 该点不在水中
    val depth = surfaces[i] - points[i * 3 + 1]
    // 浸润程度达到某阈值前线性增长，超过后饱和到上限
    val ratio = (depth / fullSubmergeDepth).coerceIn(0f, 1f)
    buoyancy += ratio * perPointBuoyancy
}
```

Spark-Core 侧只负责提供 `fluidId` + `surfaceY`，**不做**阈值/饱和/力的任何语义判断。

***

## 6. 连锁改动清单（易踩坑）

### 6.1 `SectionSnapshot.EMPTY` 语义收紧

新增 `hasSolid: Boolean`、`hasFluid: Boolean`、`fluidIndex: SectionFluidIndex?` 三个字段，
仅当 `!hasSolid && !hasFluid` 才返回 `EMPTY`。

> **三处构造点必须同步**（P2）：
> ① `EMPTY`（`:27`）、② `snapshotFromChunk` 返回（`:81`）、③ `copy()`（`:193`）。
> 其中 `EMPTY` 现在是 `Array(1) { null }`，改构造器时**顺手统一成 4096**——
> 否则 `getBlockSnapshot` 索引 4095 会 AIOOBE（今天靠所有调用点的 `isEmpty()` 守卫兜住，很脆）。

### 6.2 `buildCollisionShape()` 的守卫

现状：

```kotlin
if (snapshot == null || snapshot == SectionSnapshot.EMPTY) { collisionShape = null; return false }
```

纯水 section 现在不再是 EMPTY，会错误地进入构建流程。

**改**：`if (snapshot == null || !snapshot.hasSolid)`。

> 注意：这里的判据用 `hasSolid` 是**正确且唯一**的选择——它回答的是「要不要建刚体」，
> 与流体无关。流体通道的判据是 `hasFluid`（§4.1），两者各司其职。
> 有了 §4.4 的独立发布通道后，本函数的早退**不再需要**为流体补任何额外发布逻辑。

### 6.3 `hasNoCollisionBlocks()` 语义

现状：`return snapshotForBuild?.isEmpty() ?: true`，用于 `isTerrainReady()`。

若纯水 section 不再 EMPTY，此函数返回 false ⇒ `isTerrainReady` 要求 `isActive && isBuilt()`，
但纯水 section 永远不会 build ⇒ `isTerrainReady` 恒 false ⇒ **载具被判定地形未就绪而卡住**。

**改**：`return snapshotForBuild?.hasSolid != true`（语义改为「无固体方块」）。

### 6.4 `PhysicsChunkSection.getFluidIndex()`

```kotlin
@Volatile private var fluidIndexRef: SectionFluidIndex? = null
fun getFluidIndex(): SectionFluidIndex? = fluidIndexRef
```

此函数**不得**加 `isEmpty()` / `hasSolid()` 守卫。这一点要在注释里写死：
纯流体 section 的 `collisionShape` 恒为 null，`isEmpty()` 恒为 true，
顺手加守卫就会把整个功能挡掉。

### 6.5 `SectionSnapshot.copy()`

现状：`return SectionSnapshot(blockSnapshots.clone(), pos)`。

**改**：带上 `fluidIndex`（共享引用，不 clone）、`hasSolid`、`hasFluid`；
并在 `!hasSolid` 时共享一个**只读空数组**，省掉每次 16~32 KB 的克隆：

```kotlin
private val EMPTY_BLOCKS = arrayOfNulls<BlockSnapshot>(4096)   // 共享只读空数组

fun copy(): SectionSnapshot = when {
    !hasSolid -> SectionSnapshot(EMPTY_BLOCKS, fluidIndex, pos, false, hasFluid)
    else      -> SectionSnapshot(blockSnapshots.clone(), fluidIndex, pos, true, hasFluid)
}
```

**安全性**：`refreshSlipIfNeeded()`（`:466-475`）只对**非空**槽位写入，
`markRemoved()`（`:404-410`）写的是 `null`——对全 null 的共享数组都是无操作，
且两者都被 `isEmpty()` 提前挡住。

> 这条独立于 P0-1 仍然值得做：流水**每 tick** 都会触发整节重建（§10.3），
> 每次省 16~32 KB 的克隆在重建风暴下是实打实的收益。

### 6.6 `getBlockCount()`

`blockSnapshots.count { it != null }` —— 纯水 section 返回 0，正确，无需改动。

### 6.7 区块激活范围（风险已降级，仍需实测）

有了 §4.4 的独立发布通道后，流体索引**不需要 section 被激活**，只需要该 chunk 在
`loadedChunks` 里（`prepareSnapshot()` 已在 `PhysicsChunk.load()` 中对所有 sectionY 调用过）。

剩余风险：载具漂浮时其 AABB 可能完全位于水面之上，水面所在 **chunk** 未进入
`loadedChunks`（由 `startBuildingChunks`（buildRadius = 2）或 `loadAndActivateChunkTerrain` 填充）。

- Phase 3 实测；
- 若不满足，下游复用 `scheduleTerrain(chunkPos, yRange, holdTicks)`（主线程）或
  `SparkLevel.scheduleChunkLoad`（跨线程外观）主动预约水体范围。

### 6.8 并发容器改造（P0-3 ①）

`sampleFluidBatch` 会把「每帧几次」的容器访问放大到「每帧每艘船 × N 个采样点」，
暴露面放大 2~3 个数量级，必须先把既有隐患堵掉：

| 容器 | 现状 | 改法 | 理由 |
| --- | --- | --- | --- |
| `PhysicsChunk.sections` | `mutableMapOf<Int, PhysicsChunkSection>()`（`:22`），主线程 `load()` 写、物理线程读 | `load()` 内先写局部 `HashMap`，完成后赋给 `@Volatile var sectionsView: Map<Int, PhysicsChunkSection>` | 只在 `load()` / `unload()` 两个点整体替换，**整体替换成本为零**，读路径变普通 `Map.get` 且迭代绝对安全 |
| `PhysicsChunkManager.loadedChunks` | `mutableMapOf<ChunkPos, PhysicsChunk>()`（`:49`），主线程 insert/remove、物理线程读 | 改 `ConcurrentHashMap`（`ChunkPos` 不可变，天然安全） | 该 map 是**增量**变更的（chunk 逐个加载/卸载），不适合每次整体复制；CHM 是此处正确的工具 |

> 注意 `loadedChunks` 换 CHM 只能消除**结构性**风险（扩容迁移中读到 null、迭代 CME）。
> 「物理线程读到的 chunk 可能马上被卸载」这一**语义**风险不变——调用方本来就接受
> 「section 未加载 ⇒ 返回 null」，属于既有行为。

### 6.9 流体-only 快路径（P1-5）

`startAsyncUpdate()` 现在无论什么变化都走「4096 格快照 + 逐格 `getCollisionShape`
+ 全量 `BlockMerger` + 16~32 KB clone」。对纯流体 section 而言**固体部分一个格子都没变**，
全是浪费：

```kotlin
fun startAsyncUpdate(manager: PhysicsChunkManager) {
    buildJob?.cancel()
    val snapshot = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
    snapshotForBuild = snapshot
    publishFluidIndex()

    // 流体-only 快路径：无固体 ⇒ 不建刚体，无需 BlockMerger
    if (!snapshot.hasSolid) {
        if (physicsBody != null) {
            physicsLevel.submitImmediateTask { destroyPhysicsBody() }
        }
        collisionShape = null
        buildState = BuildState.IDLE
        return
    }
    ...既有异步构建流程...
}
```

若 `hasSolid == true` 但 `BlockMerger` 输入无变化，可用 `layerMask` 级脏标记进一步裁剪——
那属于既有热点优化范畴，**与 `地形方块变更热路径优化建议.md` 合并讨论，不在本计划越界**。

***

## 7. 流体物理属性：FluidPhysicsData

仿照既有 `BlockPhysicsData` + DataMap 机制，注册到 `Registries.FLUID`：

```kotlin
data class FluidPhysicsData(
    val density: Float,          // kg/m^3，默认 1000（水）
    val viscosity: Float,        // Pa·s，默认 1.0e-3（水）
    val dragCoefficient: Float,  // 形状阻力系数，默认 0.47
    val buoyancyEnabled: Boolean,// 是否产生浮力，熔岩可设为 false
    val surfaceBias: Float = 0f  // 液面的额外偏置（格）。默认 0 = 忠实原版（8/9）
) {
    companion object { val CODEC: Codec<FluidPhysicsData> = RecordCodecBuilder.create { ... } }
}

object FluidPhysicsDataMaps {
    @JvmField
    val FLUID_PHYSICS_DATA: DataMapType<Fluid, FluidPhysicsData> = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "fluid_physics"),
        Registries.FLUID,
        FluidPhysicsData.CODEC
    ).build()
}
```

### 7.1 `FluidCollisionUtil` 必须是无锁表（P1-4）

v1 写「`Int2ObjectMap` 缓存」是错的：fastutil 的 `Int2ObjectMap` 不是线程安全的，
而物理线程会 lazily `computeIfAbsent`（DataMap 访问），与主线程 `/reload` 时的
`invalidateFluidCache()` 并发，轻则读到半构造对象，重则扩容时结构损坏。

现有 `BlockCollisionUtil` 用 `ConcurrentHashMap` + `computeIfAbsent` 是安全的，
**只因为它只在主线程被调用**；流体查询要从物理线程调，不能照抄。

**改为「不可变数组 + volatile 引用整体替换」**：

```kotlin
object FluidCollisionUtil {
    private val DEFAULT = FluidPhysicsData(1000f, 1.0e-3f, 0.47f, true)
    @Volatile
    private var table: Array<FluidPhysicsData?> = buildTable()   // 长度 = FLUID 注册表大小

    fun getFluidPhysicsData(fluidId: Int): FluidPhysicsData =
        table.getOrNull(fluidId) ?: DEFAULT

    fun invalidateFluidCache() { table = buildTable() }          // /reload 时调用

    private fun buildTable(): Array<FluidPhysicsData?> { ... }   // 主线程构建
}
```

物理线程路径**完全无锁、无 boxing、无 DataMap 访问**。

`invalidateFluidCache()` 挂 `AddReloadListenerEvent`，与 `BlockPhysicsDataMaps` 一致。

### 7.2 fluidId 的稳定性

`palette` 里存的是 `BuiltInRegistries.FLUID` 的运行时 id。Fluid 是静态注册表，
mod 注册在启动期固定，会话内 id 稳定；但**跨 datapack reload 不保证稳定**，
故 §10 必须写明：**reload 后下游必须丢弃旧 fluidId 缓存**，并与 `invalidateFluidCache()` 联动。

***

## 8. 实施计划

> 评审建议按 **5~7 个工作日**估（v1 的「各 1 天」未计入 P0-1 发布通道改造、
> P0-3 并发视图改造、P1-4 无锁表三项）。

### Phase 1：核心数据结构【1.5 日】

1. 新增 `SectionFluidIndex` + builder（256 列主布局 + 稀疏例外）。
   自查项：`colBottomY` 用最低含水 voxel 的局部 y；列索引 `lx + (lz shl 4)`；
   段按同 `typeIdx` 才连续。
2. 新增 `FluidPhysicsData` + `FluidPhysicsDataMaps` + **volatile 数组表版** `FluidCollisionUtil`。
3. 单元测试：手工构造含多层水 / 含水方块 / 水+熔岩混层的 section，校验 L0~L5 各级编码。
4. 决策记录落库（[§11](#11-决策记录)）：`topQ` 取 A 案、列布局、独立发布通道。

### Phase 2：接入快照生命周期【2 日】

5. `SectionSnapshot` 增 `fluidIndex` / `hasSolid` / `hasFluid`，同步三处构造点。
6. `snapshotFromChunk` 增流体通道遍历（`isAir` 双通道早退 + `hasFluid` 判据）。
7. 执行 [§6.1 ~ §6.6](#6-连锁改动清单易踩坑) 连锁改动。
8. **`PhysicsChunkSection` 独立 `@Volatile fluidIndexRef` 发布通道**（§4.4，P0-1）。
9. `getFluidIndex()`（**不加** `isEmpty()` 守卫，注释写死原因）。
10. **流体-only 快路径**（§6.9）。
11. **并发容器改造**（§6.8，P0-3 ①）。

### Phase 3：查询 API【1.5 日】

12. `sampleFluid` / `sampleFluidBatch` / `isSubmerged`。
13. batch 内部自带 chunk/section 缓存 + `getSectionByCoords` 零分配访问器（§5.4）。
14. strict world→local 转换，**不使用** clamp 版 `getRelativePos()`（§5.3）。
15. `PhysicsLevel` 上暴露便捷转发。
16. 实测 §6.7 激活范围；按 §10.4 明确客户端限制。

### Phase 4：验证与调优【1 日】

17. 内存/耗时基准（§9）：使用修正后的 1.2 KB；统计「含流体 section 的分布」而非只统计总数。
18. 可选：`colTopQ` / `colType` 位打包（各 4 bit → 合并为一个 byte，再省 256 B）。

### 实现状态（Phase 1~3 + S0 已完成）

**新增文件**

| 文件 | 内容 |
| --- | --- |
| `physics/terrain/SectionFluidIndex.kt` | 索引本体 + Builder；`sampleLocal` 是全部查询路径的唯一实现 |
| `physics/terrain/FluidPhysicsData.kt` | 密度/粘度/阻力/浮力开关/`surfaceBias` + CODEC |
| `physics/terrain/FluidPhysicsDataMaps.kt` | `Registries.FLUID` DataMap 注册 + `/reload` 失效 |
| `physics/terrain/FluidSample.kt` | 单点采样结果 |
| `util/FluidCollisionUtil.kt` | volatile 不可变数组表（物理线程无锁） |

**修改文件**

| 文件 | 改动 |
| --- | --- |
| `SectionSnapshot.kt` | 双通道遍历（`isAir` 早退 + `hasFluid` 判据）、`fluidIndex`/`hasSolid`/`hasFluid`、EMPTY 语义、三处构造点、`copy()` 共享空数组、`MutableBlockPos` 复用（S0） |
| `PhysicsChunkSection.kt` | `@Volatile fluidIndexRef` 独立发布、`getFluidIndex()`、`buildCollisionShape()` 守卫、流体-only 快路径、`activate()`/`refreshSlipIfNeeded()` 跳过纯流体 |
| `PhysicsChunk.kt` | `sections` 改不可变视图 |
| `PhysicsChunkManager.kt` | `loadedChunks` 改 CHM、`getSectionByCoords`、`sampleFluid`/`sampleFluidBatch`/`isSubmerged` |
| `PhysicsLevel.kt` | 三个便捷转发 |
| `ChunkHeightIndex.kt` | `sectionHasSolidBlock` 直接读 section，消除每 voxel `BlockPos` 分配（S0） |

**与计划的偏离（2 处，均为加强）**

1. `activate()` 与 `refreshSlipIfNeeded()` 额外增加了「`!hasSolid` 直接跳过」分支 ——
   计划未列，但海洋里每个 chunk 约 8 个纯流体 section，不跳过会每 tick 白排构建作业 / 白转 4096 次空循环。
2. S0 原文建议复用 `MutableBlockPos`，实现时改为**直接读已定位的 `section.getBlockState(x, y, z)`**：
   既消除了 `BlockPos` 分配，也省掉了 `LevelChunk#getBlockState` 内部的 section 索引换算，严格更强。
   `snapshotFromChunk` 内的 `getWorldPos` 则按 S0 原样用 `MutableBlockPos` 复用。

**尚未做**：Phase 4 的内存/耗时基准、`topQ`/`typeIdx` 位打包、§6.7 激活范围实测。

***

## 9. 验证与基准

### 正确性

| # | 场景 | 期望 |
| --- | --- | --- |
| 1 | 静水池中心 | `surfaceY` == 水面世界 Y；`depth` 随下沉线性增大 |
| 2 | **顶层静水**（上方是空气） | `topQ == 14`（≈0.875），**不是 16** ← P0-2 |
| 3 | 水体内部（上方同类流体） | `topQ == 16` |
| 4 | 流水（LEVEL > 0） | `topQ == round(amount/9 × 16)`，`surfaceY` 低于满格水面 |
| 5 | 含水方块（waterlogged slab，上方空气） | L2 置位，`topQ == 14` ← **v1 §9 写 16，已更正** |
| 6 | 海带 / 海草（**无碰撞形状**） | 仍应被 L2 置位 —— 通道独立性最关键的正例 |
| 7 | 空气 / 纯固体 section | `getFluidIndex()` 返回 null |
| 8 | 纯水 section、section **从未被激活** | `getFluidIndex()` 非 null ← **P0-1 回归用例，必须新增** |
| 9 | **柱底边界**：船慢慢沉到水底 | 刚好接触水底格顶 → `isSubmerged` true；再下沉半格 → true；**离开最低含水格底 → 立刻 false** ← P1-2 专项 |
| 10 | 水 + 熔岩同列 | 熔岩内采样返回 `fluidId = 熔岩`，`surfaceY` 是**熔岩段**顶面，不是水面 ← P1-3 |
| 11 | 悬空水洼 + 下方湖泊 | 水洼走 L2 主柱；湖泊内采样走 L4 例外表，能正确命中 |

### 性能

- 构建：对比改造前后 `snapshotFromChunk` 耗时，目标增量 < 10%
  （`isAir` 早退应能对冲流体通道的开销）。
- 查询：**批量摊薄后每点 < 200 ns**；单点（含一次 section 定位 + `ChunkPos` 分配）< 500 ns。
  v1 的「单点 < 100 ns」在含一次分配 + HashMap 查找的前提下不现实，已改口径；
  **并在 API 文档明确「下游应使用 batch 路径」**。
- 内存：统计含流体 section 的**数量与分布**（按 biome / y 分层），按 1.2 KB/section 核算总量。
- 重建风暴：在流水密集处挂 profiler，对比 §6.9 快路径前后的整节重建次数与耗时。

***

## 10. 已知限制与后续

1. **跨 section 水柱**：L2 只记录本 section 内的柱顶。水柱高度 > 16 格且采样点位于柱底时，
   `colReachesTopBits` 会标记续接，但 Phase 1 先按**截断**处理
   （深水中浸没深度通常已饱和，误差影响小），后续再补完整跨 section 续接。
   注意该标记的判据是「`topY == 15` 且上方同类流体」，**不能**用 `topQ == 16`（§3.4⑤）。
2. **多层水**：仅最高柱走 O(1) 路径，其余段走 L4 稀疏例外表的 O(log n) 二分。
   例外段数在极端地形（大量含水方块散布于不同高度）下会增长，Phase 4 需实测上界。
3. **动态流体（结论已更正，P1-5）**：
   更新链路**本来就存在**，不需要新建管道：

   ```text
   FlowingFluid.spread / Source.tick
     └─ Level#setBlock(pos, state, 2|3)
          └─ Level#markAndNotifyBlock
               └─ Level#onBlockStateChange        ← 无条件调用，不在任何 flag 分支里
                    └─ ServerLevelMixin#onBlockStateChange   (mixin 早退条件：old/new 均 isCollisionShapeFullBlock)
                         └─ PhysicsChunkManager#onBlockUpdated → markDirtySections
                              └─ updateDirtySections → startAsyncUpdate
   ```

   水的碰撞形状为空，mixin 的早退条件命中不了，所以**水体变化今天就已经会触发整节重建**
   （与 `地形方块变更热路径优化建议.md` §2 的调用链分析一致）。

   真正的问题是**重建风暴**：流水每 tick 每个流动方块一次 `setBlock` ⇒ 所在 section 整节重建，
   而固体部分一个格子都没变。缓解见 §6.9 的流体-only 快路径。
4. **客户端（结论确定，P1-6）**：
   - `ClientPhysicsLevel` 继承 `PhysicsLevel`，`terrainManager` 在 `PhysicsLevel.start()`
     无条件初始化 ⇒ **客户端确实有 terrainManager**；
   - `PhysicsLevelApplier.chunkLoad / chunkUnload` 监听 `ChunkEvent`，双端都触发
     ⇒ **客户端也会 `prepareSnapshot()`，首次加载时的流体索引是对的**；
   - 但 `ServerLevelMixin`（`@Mixin(ServerLevel.class)`）是**服务端专属**
     ⇒ 客户端**没有任何方块/流体变更通知**（既没有 `onBlockStateChange`，
     也没有 `TerrainUpdatePayload` 携带流体数据）。

   结论：**客户端具备首次加载时的流体查询能力，但索引会在区块重新加载前永久过期。**
   若未来客户端需要实时流体查询（如客户端预测的船体浮动），需额外引入一条
   client 侧的 section 失效通知；`TerrainUpdatePayload` 目前只带 `SectionPos`，
   扩展它要改网络包，属独立议题，**不塞进本计划**。Phase 1~3 按「仅服务端」目标验收。
5. **液面精度**：原版自由液面在 voxel 底 + 8/9（0.8889），1/16 量化后为 0.875，
   绝对误差 1.4 cm。下游若需格子对齐的液面，用 `FluidPhysicsData.surfaceBias` 自行偏置。
6. **fluidId 稳定性**：reload 后下游必须丢弃旧 fluidId 缓存（§7.2）。

***

## 11. 决策记录

| # | 决策 | 理由 / 被否决的替代 |
| --- | --- | --- |
| D1 | 不引入水体刚体 | 只需要「位置 + 类型 + 液面高度」；刚体会进 broadphase、生成流形，且复合形状共用一个流形（最多 4 接触点）表达不了浸没体积 |
| D2 | `FluidState` 是独立通道 | waterlogged 第三类（有碰撞且含水）击穿 `hasCollision` 判据；海带/海草击穿「放宽判据」思路 |
| D3 | 数据在 `SectionSnapshot`，**发布通道独立** | `snapshotForCollision` 被 `buildCollisionShape()` 成功与否 gate 住，纯流体 section 永不可见（P0-1）；`snapshotForBuild` 无 `@Volatile` 不可直读 |
| D4 | 256 列主布局 + 稀疏例外 | 4096-voxel 布局为最坏情况付费 9.5 KB；列布局 ~1.2 KB，7.8× 收益 |
| D5 | `topQ` 忠实对齐原版（A 案） | 顶层静水 8/9 → `topQ == 14`。B 案（含水即满格）会让液面虚高 0.11 格（11 cm），船会浮高；已否决 |
| D6 | 柱按同流体才连续；柱底 = 最低含水 voxel 底面 | 防止跨流体列返回错误 `surfaceY`（P1-3）；防止水底多出一格浮力（P1-2） |
| D7 | `FluidCollisionUtil` 用 volatile 不可变数组表 | fastutil map 非线程安全，物理线程会与主线程 `/reload` 并发（P1-4） |
| D8 | 查询走 batch + strict 局部转换 | `getSectionForBlockPos` 每次分配 `ChunkPos`；`getRelativePos` 是 clamp 语义会静默返回邻格（P0-3） |
| D9 | `PhysicsChunk.sections` 改不可变视图；`loadedChunks` 改 `ConcurrentHashMap` | 前者整体替换成本为零，后者增量变更只能用并发容器（P0-3 ①） |
| D10 | 流体-only 快路径 | 流水每 tick 触发整节重建，固体部分完全没变（P1-5） |
| D11 | 客户端只保证首次加载正确 | 无变更失效通知是**确定的事实**，不是待验证项（P1-6） |
| D12 | 空气 voxel 双通道早退；流体判据用 `hasFluid` 而非 `isSolid` | `isSolid` 与 `hasFluid` 互不可推导；`isAir` 对 air/cave_air/void_air 均为 true 且三者都不可能含流体 |
| — | ~~用 `BlockCollisionUtil.hasCollision(state)` 替代 `getCollisionShape(...).isEmpty`~~ | **暂不采用**：语义不等价（`Block.hasCollision` 是属性标志，非形状判据），改动固体通道有行为风险。仅作为 §6.9 之外的可选性能项记录 |
| — | ~~评审建议：在 `buildCollisionShape()` 早退分支补 `replaceCollisionSnapshot()`~~ | 更强但不够：懒构建下从未激活的 section 仍为 null，且仍需 clone。已被 D3 取代；其中「`copy()` 共享空数组」一条单独保留为 §6.5 |
