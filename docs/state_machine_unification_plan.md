# Spark-Core 状态机统一改造计划

> **状态**: 草案 · **创建**: 2026-07-01\
> **关联**: ARMS-Core 角色控制器设计 · Machine-Max 动画系统

***

## 目录

1. [背景与动机](#1-背景与动机)
2. [现状分析](#2-现状分析)
3. [目标架构](#3-目标架构)
4. [核心设计原则](#4-核心设计原则)
5. [状态机数据模型](#5-状态机数据模型)
6. [改造清单](#6-改造清单)
7. [Bedrock 动画控制器兼容](#7-bedrock-动画控制器兼容)
8. [ARMS-Core 接入](#8-arms-core-接入)
9. [风险与缓解](#9-风险与缓解)

***

## 1. 背景与动机

### 1.1 问题

Spark-Core 目前存在**两套状态机系统**，各司其职但无统一抽象：

| 系统                                         | 位置                 | 用途                        |
| ------------------------------------------ | ------------------ | ------------------------- |
| `animation.state.origin.OAnimStateMachine` | `animation/state/` | Bedrock 动画控制器 JSON → 动画播放 |
| `state_machine.graph.StateMachineGraph`    | `state_machine/`   | 通用状态图 → 任意领域逻辑            |

### 1.2 需求

1. **ARMS-Core**（机娘模组）需要一个角色控制器，使用状态机驱动角色的 WALK / DRIVE / RAGDOLL / SWIM 状态切换
2. UGC（用户生成内容）需求：支持 Blockbench 等外部编辑器导出的 Bedrock `animation_controllers.json`，也支持代码硬编码的默认状态机
3. 动画状态机和角色控制状态机应共用同一套运行时，共享实体状态数据

### 1.3 目标

将 `StateMachineGraph` + `StateGraphController` 打造为**统一的通用状态机运行时**，两条输入路径归一到同一执行引擎：

```
Bedrock JSON ──→ OAnimStateMachine.toStateGraph() ──┐
                                                      ├──→ StateMachineGraph ──→ StateGraphController ──→ KStateMachine
Java 代码构造 ──→ new StateMachineGraph(...) ────────┘
```

***

## 2. 现状分析

### 2.1 通用状态机 `state_machine/` （新系统）

```
StateMachineGraph (纯数据，Codec 可序列化)
├── initialNode: StateNode
└── nodes: List<StateNode>
      ├── name: String
      ├── transitions: List<StateTransition>
      │     ├── event: String        ← 转移触发事件
      │     ├── target: String       ← 目标状态名
      │     └── condition: StateCondition ← 可插拔条件（Codec dispatch）
      ├── onEntry: List<StateAction>      ← 可插拔动作（Codec dispatch）
      ├── onExit: List<StateAction>
      └── subGraphs: Map<String, StateMachineGraph>  ← key=控制器名，多个并行子状态机

StateGraphController（运行时）
├── stateMachineGraph: StateMachineGraph
├── children: Map<String, StateGraphController>  ← 直接子控制器（工厂预建）
├── activeChildren: Map<String, StateGraphController> ← 当前状态激活的子控
├── tags: GameplayTagContainer        ← "黑板"，供 HasTagCondition 查询
├── currentNode: StateNode            ← 当前状态
├── progress()                        ← 每帧驱动（递归子控）
├── reset()                           ← 回到初始状态（递归子控）
├── triggerEvent(String?)             ← 事件驱动的转移触发
└── 内部: KStateMachine               ← 执行引擎（restartBlocking() 实现 reset）
```

> **子控制器组织方式**：树形结构——每个控制器至多一个父控。父控只持有直接子控引用（`children`），孙子及更深层由子控自行管理。进入状态时 `onEntry` 激活并 `reset()` 子控，退出时 `onExit` 递归清理。详见 5.6 节。

已有参考实现（KStateMachine DSL 级别验证）：

- `PlayerBaseAnimStateMachine` — 玩家动画状态机，实现 `StateMachineHandler` 接口，由 `StateMachineApplier` 每 tick 驱动
- `EntityBaseUseAnimStateMachine` — 实体使用物品动画状态机，同上

> **注意**：两者均直接使用裸 KStateMachine DSL 构建，未经过 `StateMachineGraph` / `StateGraphController`。它们功能稳定，无需迁移——统一后的 `StateGraphController` 是新增路径，不影响现有实现。

### 2.1.1 StateMachineHandler 体系与 StateGraphController 的关系

`StateMachineHandler` 是**实体挂载框架**：将状态机绑定到 Entity、由 `StateMachineApplier` 每 tick 统一驱动。`StateGraphController` 是**状态机运行时**：持有 StateMachineGraph + 内部 KStateMachine。

两者的关系：

```
StateMachineHandler (挂载层 — 绑定到 Entity)
    ├── machine: KStateMachine       ← 由 StateGraphController 内部的 KStateMachine 提供
    ├── isActive: Boolean            ← 开关
    └── progress()                   ← 委托给 controller.progress()

StateGraphController (运行时层 — 纯逻辑，不感知 Entity)
    ├── stateMachineGraph            ← 纯数据拓扑
    ├── tags: GameplayTagContainer   ← 黑板
    └── 内部 KStateMachine           ← 执行引擎
```

**统一后**：AnimStateMachine（继承 StateGraphController）通过 AnimController 驱动，不走 StateMachineHandler；通用 JSON 注册的状态机可实现 StateMachineHandler，内部委托给 StateGraphController。现有预设（PlayerBaseAnimStateMachine 等）保持不变，不强制迁移。
### 2.2 Bedrock 动画控制器 `animation/state/` （旧系统）

```
OAnimStateMachineSet (JSON Codec)
└── animationControllers: Map<String, OAnimStateMachine>
      ├── initialState: String
      └── states: Map<String, OAnimState>
            ├── animations: Map<动画名, JSMolangValue>
            ├── transitions: Map<目标状态, JSMolangValue>
            ├── onEntry / onExit: JSMolangValue
            ├── soundEffects / particleEffects
            ├── blendTransition: Float
            └── blendViaShortestPath: Boolean

OAnimStateMachine.build() → AnimStateMachine
      └── 内部用 KStateMachine DSL 即时构建状态机
```

问题：`OAnimStateMachine` 直接构建 KStateMachine，未经过 `StateMachineGraph` 中间层，导致两套系统无法互通。

### 2.3 Bedrock 格式与 StateMachineGraph 的差异

| Bedrock 特征                                               | StateMachineGraph 现状     | 兼容？             |
| -------------------------------------------------------- | ------------------------ | --------------- |
| transitions 每 tick 自动求值，无显式 event                        | transitions 有 `event` 字段 | **需改**（见 Phase 1-2） |
| 状态持有 `animations` / `sound_effects` / `blend_transition` | StateNode 无这些字段          | 转为 Action       |
| `q.all_animations_finished` 查询当前控制器动画状态                  | 无对应机制                    | 需 Controller 子类 |
| 嵌套控制器通过 `animations` 字段引用控制器名（`controller.xxx`）                       | 有 `subGraphs` 字段          | 构建时预编译为 subGraphs |

***

## 3. 目标架构

### 3.1 分层图

```
┌──────────────────────────────────────────────────────────────────┐
│  外部输入                                                         │
│  ┌─────────────────────┐   ┌──────────────────────────────┐      │
│  │ Bedrock JSON        │   │ Java 代码硬编码               │      │
│  │ (Blockbench 导出)   │   │ new StateNode("walk", ...)    │      │
│  └─────────┬───────────┘   └──────────────┬───────────────┘      │
│            │                              │                       │
│            ▼                              │                       │
│  OAnimStateMachine (Codec)                │                       │
│  .toStateMachineGraph()                   │                       │
│            │                              │                       │
│            └──────────┬───────────────────┘                       │
│                       ▼                                           │
├──────────────────────────────────────────────────────────────────┤
│  Spark-Core 通用状态机层                                          │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ StateMachineGraph (纯数据，不含任何领域语义)                   ││
│  │   StateNode → StateTransition → StateCondition / StateAction  ││
│  └──────────────────────────┬───────────────────────────────────┘│
│                             ▼                                     │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ StateGraphController (运行时)                                 ││
│  │   · tags: GameplayTagContainer ("黑板")                       ││
│  │   · progress() / triggerEvent()                               ││
│  │   · currentNode 追踪                                          ││
│  │   · 内部: KStateMachine 执行引擎                              ││
│  └──────────────────────────┬───────────────────────────────────┘│
│                             │                                     │
│             ┌───────────────┼───────────────┐                     │
│             ▼                               ▼                     │
│  AnimStateMachine          MechControllerStateMachine     │
│  · 追踪动画完成状态                · WALK / DRIVE / RAGDOLL       │
│  · MoLang 上下文注入               · 驱动 MechaController         │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 核心概念

- **`StateMachineGraph`**：纯状态转移拓扑。不含任何领域语义（动画、物理、音频等）
- **`StateCondition`**：可插拔条件接口，`check(controller) → Boolean`。通过 `SparkRegistries` 按 name dispatch，支持 Codec 序列化
- **`StateAction`**：可插拔动作接口，`execute(controller)`。领域逻辑的载体——动画播放、KCC模式切换、音效等都是 Action
- **`StateGraphController`**：运行时，持有 graph + tags + 内部 KStateMachine。子类通过覆写 `onEntry` / `onExit` / `onCheckTransition` 注入领域行为
- **`GameplayTagContainer`**：充当"角色状态快照"。由 CharStateCollector 每帧填充，供 `HasTagCondition` 查询。动画控制器和角色控制控制器共享同一套 tags

***

## 4. 核心设计原则

1. **状态机只管"切到什么状态"，不管"状态里有什么"**\
   `StateNode` 不包含 `animations` / `blendTransition` 等字段。动画、音效、KCC 模式全部由 `onEntry` / `onExit` 中的 `StateAction` 执行
2. **条件与动作可插拔**\
   第三方通过 `SparkRegistries.STATE_CONDITION_CODEC` / `STATE_ACTION_CODEC` 注册新类型，JSON 中即可使用
3. **Bedrock 是数据源，StateMachineGraph 是中间表示，KStateMachine 是引擎**\
   不替代 KStateMachine，也不破坏 Bedrock JSON 格式兼容性
4. **多状态机共享 GameplayTag**\
   两个并行 `StateGraphController`（动画 + 角色控制）消费同一份 tags，互不感知对方存在

***

## 5. 状态机数据模型

### 5.1 StateMachineGraph

```kotlin
data class StateMachineGraph(
    val initialNode: StateNode,
    val nodes: List<StateNode>
) {
    val nodeMap = (nodes + initialNode).associateBy { it.name }
}
```

### 5.2 StateNode

```kotlin
data class StateNode(
    val name: String,
    val transitions: List<StateTransition>,
    val onEntry: List<StateAction> = listOf(),
    val onExit: List<StateAction> = listOf(),
    val subGraphs: Map<String, StateMachineGraph> = mapOf()   // key=控制器名，多个并行子状态机
)
```

> **subGraphs 来源**：Bedrock JSON 路径中，`animations` 字段引用的控制器名在 `build()` 时预编译为 `subGraphs`；代码路径可直接构造。详见 Phase 5。

### 5.3 StateTransition

```kotlin
data class StateTransition(
    val event: String? = null,       // null = 每 tick 自动求值（Bedrock 模式）
    val target: String,
    val condition: StateCondition
)
```

两种求值模式：

- `event = null`：每帧 `progress()` 中求值（Bedrock 动画控制器默认行为）
- `event = "jump"`：仅在 `triggerEvent("jump")` 时求值（事件驱动模式）
- 同节点可混合使用两种转移

### 5.4 StateCondition （可插拔条件体系）

```kotlin
interface StateCondition {
    fun check(controller: StateGraphController): Boolean

    // 以下是 JSON 序列化支持（仅 Bedrock/UGS JSON 路径需要）
    val codec: MapCodec<out StateCondition>

    // 组合子（纯代码层面）
    operator fun not() = Reverse(this)
    infix fun or(other: StateCondition) = Any(listOf(this, other))
    infix fun and(other: StateCondition) = All(listOf(this, other))
}
```

**代码层面使用**（ARMS-Core Java 硬编码、Spark-Core 预设）：直接 `new` / `object` 实现接口，`check()` 写逻辑。

```java
// 纯代码构造，不涉及任何 Codec
new HasTagCondition(GameplayTag.of("motion.on_ground"))
new MoLangCondition("ctrl.is_moving")
StateCondition.True                   // 单例
```

**JSON 层面使用**（Bedrock 动画控制器、UGC 自定义 JSON）：接口实现类附带一个 `codec`（名字 + 反序列化器），注册到 `SparkRegistries.STATE_CONDITION_CODEC` 后，JSON 中即可按名字引用：

```json
{"type": "has_tag", "tag": "motion.on_ground"}
{"type": "molang", "expression": "ctrl.is_moving"}
```

Codec 仅负责 **JSON 反序列化**，不参与运行时 `check()` 调用。

预设实现（Spark-Core 内建）：

| 类                        | JSON type name | 用途                             |
| ------------------------ | -------------- | ------------------------------ |
| `StateCondition.True`    | `true`         | 无条件为真                          |
| `StateCondition.False`   | `false`        | 无条件为假                          |
| `StateCondition.Reverse` | `reverse`      | 逻辑非                            |
| `StateCondition.Any`     | `any`          | 逻辑或（短路）                        |
| `StateCondition.All`     | `all`          | 逻辑与（短路）                        |
| `HasTagCondition`        | `has_tag`      | 检查 GameplayTag — 角色状态查询的主力     |
| `MoLangCondition` \[新增]  | `molang`       | MoLang 表达式求值 — Bedrock JSON 桥接 |

### 5.5 StateAction （可插拔动作体系）

```kotlin
interface StateAction {
    fun execute(controller: StateGraphController)

    // 以下是 JSON 序列化支持（仅 Bedrock/UGC JSON 路径需要）
    val codec: MapCodec<out StateAction>
}
```

同样分两层：**代码层面**直 `new` 实现类，**JSON 层面**通过 codec name 反序列化。

预设实现（Spark-Core 内建，全部新增）：

| 类                | JSON type name  | 用途                                        |
| ---------------- | --------------- | ----------------------------------------- |
| `PlayAnimAction` | `play_anim`     | 进入状态时播放动画；记录到 AnimStateMachine 追踪列表       |
| `StopAnimAction` | `stop_anim`     | 退出状态时停止动画                                 |
| `MoLangAction`   | `molang_action` | 执行 MoLang 脚本（onEntry/onExit 中的 MoLang 语句） |
| `SoundAction`    | `sound`         | 播放状态音效                                    |
| `ParticleAction` | `particle`      | 播放状态粒子效果                                  |

### 5.6 子控制器设计约束

子控制器是 `StateGraphController` 的通用能力，不限于动画场景。ARMS-Core 的角色控制状态机同样可以使用（如 WALK 状态嵌套 "姿势子状态机"）。

#### 5.6.1 核心约束

| 约束 | 说明 |
|------|------|
| **树形结构** | 每个子控制器至多一个父控，不允许菱形依赖或多父共享 |
| **父只持子** | 父控 `children` 只含直接子控，不穿透持有孙子——孙子由子控自行管理 |
| **进入即 reset** | 父控 `onEntry` 激活子控时调用 `reset()` 回到初始状态，避免前次残留 |
| **退出递归清理** | 父控 `onExit` 中清理 `activeChildren`，子控的 `onExit` 会递归清理孙子，逐层传递 |
| **递归 progress** | 父控 `progress()` 先递归 `activeChildren.values.forEach { it.progress() }`，再驱自身转移 |

#### 5.6.2 `reset()` 实现方案

KStateMachine 提供 `restartBlocking()` 方法（`stop()` + `start()` → 回到 initial state），无需重建实例：

```kotlin
open fun reset() {
    stateMachine.restartBlocking()
    tags.clear()
    // currentNode 在 onEntry 中重新赋值为 initialNode
}
```

#### 5.6.3 工厂构建流程（树递归）

构建方（`OAnimStateMachineSet.buildRootMachines()` 或代码路径）负责递归创建全树：

```
buildRootMachines(animatable):
  1. 全部 OAnimStateMachine → toStateMachineGraph() → Map<String, StateMachineGraph>
  2. 找出根（不被任何 subGraphs 引用的 key）
  3. 对每个根递归 buildSubtree(graph, animatable):
       a. 收集 graph 所有 node.subGraphs 的 key → 对每个 value graph 递归 buildSubtree
       b. 构造 StateGraphController(graph, animatable, children=递归结果)
       c. 返回实例
  4. 返回 Map<根名 → StateGraphController>
```

> **与旧设计的区别**：不用全局 flat `childMachines` 共享表——每棵子树独立构建 `children`，天然保证树形隔离。

#### 5.6.4 `StateGraphController` init 需跳过 `subGraphs` 嵌入

当前 `StateGraphController` 在构建 KStateMachine 时对 `subGraph`（单数）递归创建嵌套状态。改为 `subGraphs`（复数 map）后，父类 **不再处理子图**——子图数据仅作为元数据存在，由 `onEntry` 激活子控实例。

***

## 6. 改造清单

### Phase 1: StateTransition 支持可空 event

**文件**: `state_machine/graph/StateTransition.kt`、`StateNode.kt`

```diff
  data class StateTransition(
-     val event: String,
+     val event: String? = null,
      val target: String,
      val condition: StateCondition
  )
```

同时修改 `StateNode`，将 transition 预计算为两组，避免 Phase 2 中每次转移求值时 O(n) 全量遍历：

```diff
  data class StateNode(...) {
-     /** 按 event 分组的转移映射 */
-     val transitionMap: Map<String, List<StateTransition>>
+     /** event != null 的转移，按 event 分组（事件驱动路径，O(1) 查找） */
+     val eventTransitions: Map<String, List<StateTransition>>
+     /** event == null 的转移，每 tick 自动求值（Bedrock 模式路径） */
+     val autoTransitions: List<StateTransition>
  }
```

- `eventTransitions` 保持原有 Map 查找性能
- `autoTransitions` 仅在 `progress()` 路径遍历，典型大小 1~3 条，开销可忽略

> **配套修改**：`StateTransition.CODEC` 中 `Codec.STRING.fieldOf("event")` 需改为 `Codec.STRING.optionalFieldOf("event")`，否则 JSON 反序列化无法表达 null-event 转移（Bedrock 桥接路径需要 event 为空）。`StateNode.CODEC` 中的 `transitions` 字段已是 `optionalFieldOf`，无需改动。

### Phase 2: StateGraphController — 无事件转移 + progress() + 子控制器管理

**文件**: `state_machine/graph/StateGraphController.kt`

#### 2.1 双路径转移匹配

修改 `transitionConditionally` 内的匹配逻辑，按 event 是否为空走不同路径：

```diff
- val next = node.transitionMap[event.type]?.firstOrNull { it.condition.check(this) }
+ val next = if (event.type == null) {
+     // Bedrock 模式：只遍历 autoTransitions（通常 1~3 条）
+     node.autoTransitions.firstOrNull { it.condition.check(this) }
+ } else {
+     // 事件驱动模式：O(1) Map 查找（保持原有性能）
+     node.eventTransitions[event.type]?.firstOrNull { it.condition.check(this) }
+ }
```

#### 2.2 新增构造函数参数与方法

```diff
  open class StateGraphController(
      val stateMachineGraph: StateMachineGraph,
+     /** 直接子控制器实例（key = 控制器名），工厂方法递归填充，运行时只含本层 */
+     private val children: Map<String, StateGraphController> = mapOf()
  ) {
+     /** 当前状态激活的子控制器 */
+     private val activeChildren = mutableMapOf<String, StateGraphController>()
```

新增 `progress()`：
```kotlin
/** 每帧调用。先递归驱动子控制器，再驱动自身 event=null 转移 */
open fun progress() {
    activeChildren.values.forEach { it.progress() }
    triggerEvent(null)
}
```

新增 `reset()`：
```kotlin
/** 重置到初始状态（递归子控）。利用 KStateMachine.restartBlocking() 回到 initial state */
open fun reset() {
    activeChildren.values.forEach { it.reset() }
    activeChildren.clear()
    stateMachine.restartBlocking()
    tags.clear()
}
```

`triggerEvent` 签名改为接受 `String?`：
```diff
- open fun triggerEvent(type: String): ActionEvent
+ open fun triggerEvent(type: String?): ActionEvent
```

#### 2.3 `onEntry` / `onExit` 集成子控生命周期

```kotlin
open fun onEntry(node: StateNode) {
    // 激活当前状态的子控制器（reset 确保每次进入从初始态开始）
    node.subGraphs.keys.forEach { name ->
        children[name]?.also {
            it.reset()
            activeChildren[name] = it
        }
    }
}

open fun onExit(node: StateNode) {
    // 递归退出子控制器——子控自己的 onExit 会清理孙子，逐层传递
    activeChildren.values.forEach { it.onExit(it.currentNode) }
    activeChildren.clear()
}
```

#### 2.4 `StateGraphController` init 跳过 `subGraphs` 嵌入

当前 init 中 `createStates()` 对 `node.subGraph`（单数）递归创建嵌套 KStateMachine 状态。改为 `subGraphs`（复数 map）后，移除递归嵌入逻辑——子图仅作为元数据存在。

```diff
- node.subGraph?.let { createStates(it) }
+ // subGraphs 不再内联到 KStateMachine；由 onEntry 中的 children + activeChildren 管理
```

### Phase 3: 新增 Bedrock 桥接的 Action / Condition 实现类

这些类是 `StateCondition` / `StateAction` 接口的**纯 Java/Kotlin 实现**。代码层面直接 `new` 使用。

```java
// ARMS-Core 硬编码状态机使用示例：
new StateTransition(null, "run",
    new MoLangCondition(new JSMolangValue("ctrl.is_moving && ctrl.speed_gt(4)")))
new StateNode("idle", ...,
    List.of(new PlayAnimAction("idle", 0.15f, true)),
    List.of(new StopAnimAction()))
```

**新增文件**:

| 文件                                                  | 类                 | 接口               | 说明           |
| --------------------------------------------------- | ----------------- | ---------------- | ------------ |
| `state_machine/graph/conditions/MoLangCondition.kt` | `MoLangCondition` | `StateCondition` | MoLang 表达式条件（构造接受 `JSMolangValue`，避免字符串→AST 重复解析） |
| `state_machine/graph/actions/PlayAnimAction.kt` | `PlayAnimAction` | `StateAction` | 进入状态时播放动画；若有 MoLang 权重表达式则注册 `AnimEvent.Tick` 回调每帧更新权重 |
| `state_machine/graph/actions/StopAnimAction.kt` | `StopAnimAction` | `StateAction` | 退出状态时停止动画 |
| `state_machine/graph/actions/MoLangAction.kt`       | `MoLangAction`    | `StateAction`    | 执行 MoLang 脚本 |
| `state_machine/graph/actions/SoundAction.kt`        | `SoundAction`     | `StateAction`    | 播放音效         |
| `state_machine/graph/actions/ParticleAction.kt`     | `ParticleAction`  | `StateAction`    | 播放粒子效果       |

每个类额外附带一个 `codec`（`MapCodec` 实例），在 Spark-Core 初始化时注册到 `SparkRegistries.STATE_CONDITION_CODEC` / `STATE_ACTION_CODEC`。这**仅用于 JSON 反序列化**（Bedrock 动画控制器和 UGC JSON 路径），不影响代码层面的 `new` 和 `execute()` 调用。

**`PlayAnimAction` 的关键逻辑**——动态权重通过 `AnimInstance` 现有的 `onEvent` 机制实现，框架不改任何代码：

```kotlin
class PlayAnimAction(
    val animName: String,
    val blendTime: Float = 0.15f,
    val blendViaShortestPath: Boolean = false,
    val weightExpression: JSMolangValue? = null  // null = 固定权重 1.0
) : StateAction {

    override fun execute(controller: StateGraphController) {
        val ctrl = controller as? AnimStateMachine ?: return
        val instance = animInstance(ctrl.animatable, animName) ?: run {
            SparkCore.LOGGER.warn("状态 [{}] 引用不存在的动画 [{}]，已跳过", ctrl.currentNode.name, animName)
            return
        }
        instance.inTransitionTime = blendTime
        instance.group = AnimGroups.STATE

        // ★ 动态权重：利用 AnimInstance 现有的 onEvent 机制，每 tick 重新求值
        if (weightExpression != null) {
            instance.onEvent<AnimEvent.Tick> {
                weight = weightExpression.evalAsDouble(this).toFloat()
            }
        }

        instance.enter()
        ctrl.activeAnimInstances.add(instance)
    }
}
```

不需要框架改动——`AnimInstance` 已有 `onEvent<AnimEvent.Tick>` 和 `weight` 字段，`tick()` 中已有 `triggerEvent(AnimEvent.Tick)`。进入状态时注册回调即可。

> **注意**：`AnimInstance.exit()` 不会自动清理 `eventHandlers`。退出过渡期间 `AnimInstance.stepWeight()` 使用 `currentWeight = weight * progress` 做淡出，若 Tick handler 未移除会继续覆写 `weight` 字段，干扰淡出过程。因此 `AnimStateMachine.onExit` 中统一移除 Tick handler（见 Phase 4），确保退出过渡平滑完成。
> 
> 退出完成（state → IDLE）后，`AnimLayer.physicsTick()` 自动通过 `removeIf { it.state == AnimState.IDLE }` 清理死实例，无内存泄漏风险。

#### 设计说明：Action/Condition 如何获取上下文

`PlayAnimAction`、`MoLangCondition`、`MoLangAction` 等需要访问 `IAnimatable` 及 MoLang 引擎。上下文传递路径：

```
StateAction.execute(controller) / StateCondition.check(controller)
  → controller 实际为 AnimStateMachine 实例
  → (controller as AnimStateMachine).animatable       // IAnimatable<*>
  → animatable.getMolangContext()                     // SparkMolangContext<*>
  → 求值 MoLang 表达式 / 播放动画 / 注入变量
```

`AnimStateMachine` 通过继承 `StateGraphController` 持有 `animatable` 引用（见 Phase 4）。所有动画相关的 Action/Condition 在 `execute()` / `check()` 中将 controller 转型为 `AnimStateMachine`。

> **警告**：`PlayAnimAction` 等动画 Action 仅在 `AnimStateMachine` 上下文中有效。若被错误配置到普通的 `StateGraphController` 上，转型失败时应输出警告日志并安全跳过，不得静默失败。

### Phase 4: 改造 AnimStateMachine — 继承 StateGraphController

**文件**: `animation/state/AnimStateMachine.kt`

`AnimStateMachine` 当前是对 KStateMachine 的薄包装。改为继承 `StateGraphController`。子控制器管理（`children` / `activeChildren` / `onEntry` / `onExit` / `progress` / `reset`）全部由父类 `StateGraphController` 提供——`AnimStateMachine` 只需覆写钩子添加动画追踪逻辑。

```kotlin
/**
 * 动画状态机控制器。
 * 继承 StateGraphController，增加动画生命周期追踪和 MoLang 上下文注入能力。
 *
 * 职责：
 * 1. 追踪当前状态活跃的 AnimInstance 列表（由 PlayAnimAction / StopAnimAction 维护）
 * 2. 在转移求值前更新动画完成状态 → 注入 MoLang 上下文（q.all_animations_finished）
 *
 * 子控制器管理由父类 StateGraphController 提供，AnimStateMachine 不关心。
 */
class AnimStateMachine(
    graph: StateMachineGraph,
    val animatable: IAnimatable<*>,
    children: Map<String, StateGraphController> = mapOf()
) : StateGraphController(graph, children) {

    /** 当前状态活跃的动画追踪列表（仅本层，不含子控） */
    internal val activeAnimInstances = mutableListOf<AnimInstance>()

    override fun onCheckTransition() {
        // 只查当前 state 的动画——子控的完成状态由用户自行用变量（v.xxx_finish）传递
        var allDone = true
        var anyDone = false
        for (inst in activeAnimInstances) {
            if (inst.isFinished) anyDone = true else allDone = false
        }
        if (activeAnimInstances.isEmpty()) {
            allDone = true; anyDone = true
        }
        animatable.setControllerAnimationFinished(allDone, anyDone)
    }

    override fun onExit(node: StateNode) {
        // ★ 先清理动画追踪（移除 Tick handler，防止退出过渡期间覆写 weight）
        for (inst in activeAnimInstances) {
            inst.eventHandlers.remove(AnimEvent.Tick::class)
        }
        activeAnimInstances.clear()

        // 再调父类 onExit → 递归清理子控（含孙子）
        super.onExit(node)
    }
}
```

> **子控动画完成状态如何影响父控转移**：`AnimStateMachine.onCheckTransition` 只查自身 `activeAnimInstances`。子控动画播完后，用户应在子控的 `onExit` 中自行写入 MoLang 变量（如 `v.leg_anim_finish = 1`），父控转移条件用 `MoLangCondition("v.leg_anim_finish == 1")` 判断。这样完全解耦父子，避免上下文槽位覆盖问题。

### Phase 5: 改造 OAnimStateMachine.build() — 产出新的 AnimStateMachine

**文件**: `animation/state/origin/OAnimStateMachine.kt`、`OAnimStateMachineSet.kt`

`build()` 返回类型不变（仍为 `AnimStateMachine`），内部改为编译 `StateMachineGraph` 再包装。新增 `toStateMachineGraph(resolve)` 方法接受控制器查找闭包，处理 `animations` 字段中的控制器引用。

#### 5.1 子控制器解析策略

`animations` 中每条条目按优先级解析：

1. **控制器名匹配** → 调用 `resolve(name)` → 递归编译为 `StateMachineGraph` → 加入 `subGraphs`
2. **动画名匹配** → 创建 `PlayAnimAction`
3. **都找不到** → `warn` 日志并跳过（保持可诊断性）

#### 5.2 回环检测

`resolve` 闭包内使用两个集合防回环：

- `resolved: Map<String, StateMachineGraph?>` — 缓存已编译结果，同一控制器多处引用只编译一次
- `resolving: MutableSet<String>` — 当前递归栈，遇到重复直接断链 + error 日志

```kotlin
fun build(animatable: IAnimatable<*>): AnimStateMachine {
    // 单独构建时无子控制器上下文，传空 resolver
    return AnimStateMachine(toStateMachineGraph { null }, animatable)
}

/** 将 Bedrock JSON 编译为纯数据 StateMachineGraph。
 *  @param resolver 控制器名 → StateMachineGraph 查找闭包，返回 null 表示不是控制器引用
 */
fun toStateMachineGraph(
    resolver: (String) -> StateMachineGraph?
): StateMachineGraph {
    val nodes = states.map { (stateName, os) ->
        val subGraphs = mutableMapOf<String, StateMachineGraph>()
        val animActions = mutableListOf<PlayAnimAction>()

        os.animations.forEach { (name, weightExpr) ->
            // 1. 优先按控制器名解析 → subGraph
            val subGraph = resolver(name)
            if (subGraph != null) {
                subGraphs[name] = subGraph  // key = 控制器名
                return@forEach
            }
            // 2. 其余按动画名处理（存在性校验由 PlayAnimAction.execute() 在运行时完成）
            animActions.add(PlayAnimAction(name, os.blendTransition, os.blendViaShortestPath, weightExpr))
        }

        StateNode(
            name = stateName,
            transitions = os.transitions.map { (target, condition) ->
                StateTransition(
                    event = null,  // Bedrock 模式：每 tick 自动求值
                    target = target,
                    condition = MoLangCondition(condition)  // JSMolangValue 对象，避免重新编译
                )
            },
            onEntry = buildList {
                addAll(animActions)
                os.onEntry.forEach { add(MoLangAction(it)) }
                os.soundEffects.forEach { add(SoundAction(it)) }
                os.particleEffects.forEach { add(ParticleAction(it)) }
            },
            onExit = os.onExit.map { MoLangAction(it) },
            subGraphs = subGraphs
        )
    }
    return StateMachineGraph(
        initialNode = nodes.first { it.name == initialState },
        nodes = nodes
    )
}
```

#### 5.3 OAnimStateMachineSet 组装 — 树递归构建

采用树形递归策略——每棵子树独立构建 `children`，不共享 flat 表。`buildRootMachines()` 只返回**不被其他控制器引用的根控制器**。

```kotlin
// OAnimStateMachineSet — 递归构建控制器树，只返回根
fun buildRootMachines(animatable: IAnimatable<*>): Map<String, AnimStateMachine> {
    // 第一遍：全部编译为 StateMachineGraph，同时填充 subGraphs
    val graphs = compiledGraphs()  // 回环检测在此完成

    // 找出根（不被任何 subGraphs 引用的 key）
    val childNames = graphs.values.flatMap { graph ->
        graph.nodeMap.values.flatMap { it.subGraphs.keys }
    }.toSet()
    val rootNames = graphs.keys - childNames

    /** 递归构建子树 */
    fun buildSubtree(graph: StateMachineGraph): AnimStateMachine {
        val children = mutableMapOf<String, StateGraphController>()
        // 收集 graph 中所有 state 引用的子图 → 递归构建
        graph.nodeMap.values.forEach { node ->
            node.subGraphs.forEach { (name, subGraph) ->
                if (name !in children) {
                    children[name] = buildSubtree(subGraph)
                }
            }
        }
        return AnimStateMachine(graph, animatable, children)
    }

    return rootNames.associateWith { buildSubtree(graphs[it]!!) }
}

/** 编译全部控制器为 StateMachineGraph（带回环检测） */
private fun OAnimStateMachineSet.compiledGraphs(): Map<String, StateMachineGraph> {
    val resolved = mutableMapOf<String, StateMachineGraph?>()
    val resolving = mutableSetOf<String>()

    fun resolve(name: String): StateMachineGraph? {
        if (name in resolving) {
            SparkCore.LOGGER.error("动画控制器回环引用: {} ← {}",
                resolving.joinToString(" ← "), name)
            return null
        }
        resolved[name]?.let { return it }
        val src = animationControllers[name] ?: return null
        resolving.add(name)
        val graph = src.toStateMachineGraph(::resolve)  // 递归编译（填充 subGraphs）
        resolving.remove(name)
        resolved[name] = graph
        return graph
    }

    animationControllers.keys.forEach { resolve(it) }
    return resolved.filterValues { it != null }.mapValues { it.value!! }
}
```

> **与旧设计的区别**：
> - 旧：先建全局 flat `childMachines` 表，所有根共享同一个表 → 实例共享污染
> - 新：每棵子树独立递归构建 `children`，天然保证树形隔离；同一控制器被多处引用时每处独立实例
> - `buildSubtree` 中已有去重逻辑（`name !in children`），处理同一 parent 下多个 state 引用同一子控的场景

#### 5.4 AnimController 适配

`AnimController` 只持有根控制器，只对根调 `progress()`。子控制器的生命周期由父控 `onEntry`/`onExit`/`progress()` 全权管理。

```diff
- val stateMachines by lazy { originStateMachines.animationControllers.mapValues { it.value.build(animatable) } }
+ val stateMachines by lazy { originStateMachines.buildRootMachines(animatable) }
```

### Phase 6: AnimController 适配 — 改 tick() 为 progress()

**文件**: `animation/anim/AnimController.kt`

类型无需改变（`stateMachines` 仍然是 `Map<String, AnimStateMachine>`），只改调用方式：

```diff
  fun tick() {
      // ... 骨骼姿态更新 ...
      layers.forEach { (_, layer) -> layer.tick() }
-     stateMachines.values.forEach { it.tick() }
+     stateMachines.values.forEach { it.progress() }
  }
```

### 改动范围汇总

| 文件                        | 操作                                            | 风险           |
| ------------------------- | --------------------------------------------- | ------------ |
| `StateTransition.kt`      | 改 `event` 类型 → `String?`，CODEC 改为 optional    | 低            |
| `StateNode.kt`            | `subGraph` → `subGraphs: Map<...>`，新增 `eventTransitions`/`autoTransitions` 预计算，更新 CODEC | 低            |
| `StateGraphController.kt` | 加 `progress()` / `reset()` / `children` / `activeChildren`，改 triggerEvent 签名为 `String?`，修改 init 跳过 subGraphs 嵌入 | 中            |
| `ActionEvent`             | `type: String` → `String?`                    | 低            |
| 新增 6 个 Action/Condition 类 | 新建文件                                          | 零            |
| `AnimStateMachine.kt`     | 改为继承 `StateGraphController`，移除子控管理，仅保留动画追踪 + `onCheckTransition` + `onExit` 覆写 | 低            |
| `OAnimStateMachine.kt`    | 改 `build()` 内部实现 + 新增 `toStateMachineGraph()` | 低            |
| `OAnimStateMachineSet.kt` | 重写 `buildRootMachines()` 为树递归构建              | 中            |
| `AnimController.kt`       | 改 `.tick()` → `.progress()`                   | 低：一行改动       |

***

## 7. Bedrock 动画控制器兼容

### 7.1 字段映射

```
Bedrock JSON                                StateMachineGraph
─────────────                               ────────────────
{                                           StateMachineGraph(
  "controller.player.main": {                 initialNode = ..., nodes = [...]
    "initial_state": "idle",        ──→  )
    "states": {
      "idle": {                             StateNode(
        "animations": [            ──→        onEntry = [
          "idle",                                PlayAnimAction("idle", ...),
          {"breathing": "q.modified_distance"}    PlayAnimAction("breathing", expr=...)
        ],                                     ],
        "transitions": [           ──→        transitions = [
          {"walk": "ctrl.is_moving"},            StateTransition(null, "walk", MoLangCondition("ctrl.is_moving"))
        ],                                     ],
        "on_entry": ["v.attack=0;"] ──→        onEntry = [..., MoLangAction("v.attack=0;")],
        "on_exit": [],              ──→        onExit = [],
        "sound_effects": [...],     ──→        onEntry = [..., SoundAction(...)],
        "blend_transition": 0.15,   ──→        (作为参数传入 PlayAnimAction)
        "blend_via_shortest_path": true ──→    (同上)
      }),
      ...
    }
  }
}
```

### 7.2 `q.all_animations_finished` 处理

Bedrock 的 `q.all_animations_finished` 语义为"**当前控制器的当前状态的所有动画是否全部播完**"——只查本层，不含子控。

实现路径：

1. `AnimStateMachine.progress()` → 内部调用 `super.progress()`
2. `super.progress()` → 递归子控 → 触发 null-event 转移 → `transitionConditionally` 被调用
3. `transitionConditionally` 的 `direction` 块中先执行 `onCheckTransition()`
4. `AnimStateMachine.onCheckTransition()` 遍历 **自身** `activeAnimInstances`，注入 MoLang 上下文
5. MoLang 的 `q.all_animations_finished` 从上下文读取 → 返回正确值
6. 再执行 `MoLangCondition.check()` → 表达式中的 `q.all_animations_finished` 拿到正确值

#### 7.2.1 嵌套控制器的完成状态传递

子控制器的 `q.all_animations_finished` 不与父控合并。父控若需感知子控动画完成，由用户在子控中写入自定义 MoLang 变量：

```json
// 子控制器 example.child
{
  "states": {
    "play": {
      "on_exit": ["v.child_done = 1;"]   // 子控退出时写变量
    }
  }
}

// 父控制器
{
  "transitions": [
    {"next": "v.child_done == 1"}        // 父控用变量判断
  ]
}
```

这样父子完全解耦，不依赖共享上下文槽位。

### 7.3 多控制器并存

一个 `IAnimatable` 可以持有多个 `AnimStateMachine` —— 对应 Bedrock `animation_controllers` 中的多个顶层 key（如 `controller.player.main`、`controller.player.hold_mainhand`）。每个实例独立追踪自己的动画，互不干扰。

***

## 8. ARMS-Core 接入

### 8.1 角色控制状态机

角色控制的 WALK / DRIVE / RAGDOLL / SWIM 状态机使用 Java 代码构造 `StateMachineGraph`：

```java
StateMachineGraph controllerGraph = new StateMachineGraph(
    new StateNode("walk",
        List.of(
            new StateTransition(null, "drive",  // null = 每 tick 求值
                new StateCondition.All(List.of(
                    new HasTagCondition(GameplayTag.of("input.moving")),
                    new HasTagCondition(GameplayTag.of("motion.sprint"))
                ))
            ),
            new StateTransition(null, "ragdoll",
                new StateCondition.Any(List.of(
                    new HasTagCondition(GameplayTag.of("status.dead")),
                    new HasTagCondition(GameplayTag.of("motion.separation_exceeded"))
                ))
            ),
            new StateTransition(null, "swim",
                new HasTagCondition(GameplayTag.of("motion.swim"))
            )
        ),
        List.of(new SetKCCWalkModeAction())
    ),
    List.of(
        new StateNode("drive", ..., List.of(new SetKCCDriveModeAction())),
        new StateNode("ragdoll", ..., List.of(new SetKCCRagdollModeAction())),
        new StateNode("swim", ..., List.of(new SetKCCSwimModeAction()))
    )
);

var kccStateMachine = new StateGraphController(controllerGraph);
```

### 8.2 自定义 Action 注册

ARMS-Core 在 mod 初始化时注册自己的 Action 类型：

```java
// 在 ARMS-Core 初始化时
SparkRegistries.STATE_ACTION_CODEC.register(
    ResourceLocation.fromNamespaceAndPath("arms_core", "set_kcc_mode"),
    SetKCCModeAction.CODEC
);
```

### 8.3 每 tick 流程

```java
// ArmsCore.onHostTick()
void onHostTick() {
    // 1. 采集角色状态 → GameplayTag
    charStateCollector.collect(entity, kcc, kccStateMachine.getTags());
    
    // 2. 驱动角色控制状态机
    kccStateMachine.progress();
    
    // 3. 驱动动画状态机（多个 AnimStateMachine）
    for (var animCtrl : animControllers) {
        animCtrl.progress();
    }
}
```

### 8.4 UGC 路径

用户的 `animation_controllers.json` → `OAnimStateMachineSet` (Codec) → 每个 `OAnimStateMachine.toStateMachineGraph()` → `AnimStateMachine` → 自动运行。

自定义条件/动作：ARMS-Core 注册新的 `StateCondition` / `StateAction` 类型后，用户的 JSON 中可直接引用：

```json
{
  "transitions": [
    {"run": {"type": "molang", "expression": "ctrl.speed_gt(4) && ctrl.is_moving"}},
    {"jump": {"type": "has_tag", "tag": "motion.airborne"}}
  ]
}
```

***

## 9. 风险与缓解

| 风险                                                            | 缓解                                                  |
| ------------------------------------------------------------- | --------------------------------------------------- |
| `StateTransition.event` 改为可空破坏现有代码                            | Kotlin 中 `String?` 对现有 `String` 赋值自动兼容；Java 调用者无需改动 |
| `StateNode.subGraph` → `subGraphs` Map 破坏现有 JSON 和代码              | 当前无已知外部使用者；`StateNode.CODEC` 同步更新 `optionalFieldOf("sub_graphs")` |
| `StateGraphController` 重构幅度较大（子控管理、reset、init 改动）              | 现有 `PlayerBaseAnimStateMachine` / `EntityBaseUseAnimStateMachine` 不经过 `StateGraphController`，不受影响 |
| `AnimStateMachine` 构造函数签名变更影响外部                               | 实际唯一构造点在 `OAnimStateMachineSet.buildRootMachines()`，Machine-Max / ARMS-Core / YesSteveModel 均无直接引用 |
| `buildRootMachines()` 树递归 → 同控多处引用时创建多份实例                    | 内存开销可控（子控轻量）；语义正确——不同父环境下子控应独立运行 |
| MoLang `q.all_animations_finished` 在嵌套状态机中行为变化                | 只查本层，与 Bedrock 语义一致。父子协作通过用户自定义变量完成 |
| 性能：每帧递归遍历子树                                                      | 控制器树通常 1\~3 层，`activeChildren` 仅含当前状态激活的子控（通常 ≤ 2），开销 O(层级数) |
| `PlayAnimAction` 每次状态进入创建新 AnimInstance，GC 压力                         | 动画状态转移频率低（每秒 ≤ 几次），单次分配开销可忽略；`AnimLayer.physicsTick()` 自动清理 IDLE 实例 |
| 动画名不存在时的错误检测从 `build()` 时推迟到运行时                        | `PlayAnimAction.execute()` 中对 `animInstance()` 返回 null 时输出 warn 日志，可诊断性等效 |
| Bedrock `blend_transition` 曲线（分段关键帧）在当前 `PlayAnimAction` 中未体现 | 后续 Phase 增加 `BlendTransition` 参数，当前仅支持线性过渡时间        |
| `restartBlocking()` 在非主线程调用的线程安全性                             | `reset()` 仅在 `onEntry`（主线程 transfer 回调）中调用；ActiveChildren 使用普通 `MutableMap`（单线程访问） |

