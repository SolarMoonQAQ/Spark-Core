# KStateMachine 性能分析与优化报告

> 结论先行：**每帧固定成本几乎与状态总数无关**（这是好消息，推翻了「状态多了就慢」的直觉），
> 真正的成本集中在三处 —— **桥接层固定开销**、**转移时的全树失效**、**深嵌套的乘法放大**。
> 已针对前两项落地优化，逐帧分配量下降 **21~26%**，转移相关路径下降 **16~24%**，全部行为测试通过。

> **2026-10-10 复核修订（回退记录）**：§3.3、§3.4 两项经复核**实测收益为 0**，已回退；§3.2 的
> `BaseStateImpl` 快路径在 `doNotThrowOnMultipleTransitionsMatch = true` 时会静默丢弃深层转移
> （真 bug，已由新测试复现），一并回退。**只保留 §3.1 的单遍扫描。** 同一探针三组对照实测（B/op）：

| 场景 | 回退前（HEAD） | 当前 | 差值 |
|---|---|---|---|
| 空转一帧（StateGraphController 形，20 状态） | 1832 B/op | **1344 B/op** | **−488 B（−27%）** |
| 真实转移（2 个兄弟状态） | 3224 B/op | **2888 B/op** | −336 B（−10%） |
| 真实转移（150 个兄弟状态） | 29344 B/op | 29008 B/op | −336 B（−1.1%） |

- 与「两项都保留」的版本相比，上表数字**逐位相同** → §3.2 在本生态的图上贡献 0
  （`StateGraphController` 每个状态都有转移，`transitions.isEmpty()` 永不成立）。
- §3.3 是空操作：Kotlin `Collection.toList()` 对空集合返回共享 `emptyList()` 单例，原代码本来就不分配。
- §3.4 在该图上永不生效：每个节点转移都注册了 `onTriggered`，`transition.listeners` 非空使守卫恒真。
  `activeStates()` 本身实测仅 **272 B / 57 ns**（固定，不随状态数增长）。
- §4 的百分比数字本身可复现，但来源是 **§3.1**，不是 §3.3/§3.4；§4 表里「120 项测试」写于
  attribution / pseudo 两个套件补全之前，当前为 **13 个套件 / 144 项**。
- 最大残差 §4.1 的 `recursiveAfterTransitionComplete` 整子树遍历**已按子树剪枝**（只下探含覆写者的分支；
  覆写者由类层次自动探测，不依赖子类手工声明）。实测 150 个兄弟状态的一次转移：
  **29008 → 2608 B/op（−91%）**、**7326 → 1164 ns（−84%）**，并且不再随树宽增长
  （50 个兄弟 11408 → 2560 B/op，2 个兄弟 2888 → 2488 B/op）。语义由 `PseudoStateSemanticsTest`（16 项）
  与新增的 `KStateMachineAfterCompletePruningGuardTest`（4 项）守着。

---

## 1. 测量方法

新增两个测试文件（可重复运行、可用于回归对比）：

| 文件 | 作用 |
|---|---|
| [`src/test/kotlin/cn/solarmoon/spark_core/state_machine/perf/PerfHarness.kt`](../src/test/kotlin/cn/solarmoon/spark_core/state_machine/perf/PerfHarness.kt) | 测量夹具：预热 → 多轮测量取中位数，同时统计 **ns/op** 与 **B/op** |
| [`.../perf/KStateMachinePerfTest.kt`](../src/test/kotlin/cn/solarmoon/spark_core/state_machine/perf/KStateMachinePerfTest.kt) | 15 组基准，覆盖逐帧成本 / 缩放 / 分配解剖 |
| [`.../perf/KStateMachineOptimizationGuardTest.kt`](../src/test/kotlin/cn/solarmoon/spark_core/state_machine/perf/KStateMachineOptimizationGuardTest.kt) | 行为守卫测试，防止性能优化悄悄改变语义 |

分配量通过 `com.sun.management.ThreadMXBean#getThreadAllocatedBytes` 读取，无需 JFR / async-profiler。

运行：

```bash
gradlew test --tests "cn.solarmoon.spark_core.state_machine.perf.*"
```

**为什么必须同时看 B/op**：服务端卡顿的常见来源不是单次 CPU 时间，而是**分配速率 → young GC 频率 → tick 抖动**。
本报告的优化主要收益也体现在 B/op 上。

> 测量环境：JetBrains JBR 21.0.8，单线程，`-Xmx512m`（Gradle 测试 JVM 默认值）。
> ns/op 受 JIT 与调度影响，多轮之间波动约 ±20%；B/op 高度稳定（±1%），可作为可靠回归指标。
> 对照：`HashMap.get` 24.6 ns/op、`Random.nextInt()` 24.5 ns/op，说明夹具自噪声可忽略。

---

## 2. 关键测量结果

### 2.1 Spark-Core 真正的热路径：`StateGraphController.progress()`

每个实体每 tick 都会走这条路径（`StateMachineApplier.entityTick` → `AnimController` → `progress(dt)`）。

| 场景 | ns/op | B/op |
|---|---|---|
| `progress()`，1 个状态 | 300 | 1680 |
| `progress()`，20 个状态 | 341 | 1680 |
| `progress()`，80 个状态 | 345 | 1680 |

**结论：完全不随状态数增长（x1.0）。** 原因是 `recursiveFindUniqueResolvedTransition` 只检查**当前活跃状态**，
而不是遍历所有状态 —— 所以「状态机有 100 个节点」本身不是问题。

### 2.2 那么状态数到底影响什么？

| 场景 | ns/op | B/op |
|---|---|---|
| `progress()` 无转移发生 | 331 | 1608 |
| `progress()` 每帧都发生转移 | 1076 | 3696 |
| `triggerEvent("go")` 每帧都发生转移 | 1117 | 3728 |

一次**真实转移**的边际成本 ≈ **740 ns / 1950 B**。这是唯一「按事件次数计费」的部分。

### 2.3 转移的固定成本随子树宽度增长

`hub --(auto,true)--> hub` 自转移，兄弟状态完全不参与：

| 兄弟状态数 | ns/op | B/op |
|---|---|---|
| 1 | 590 | 2904 |
| 10 | 983 | 4848 |
| 50 | 2788 | 13168 |
| 150 | 7122 | 33968 |

**结论：每次转移都要付 O(整棵子树) 的代价**（每个兄弟状态约 +44 ns / +210 B），
来源是 `recursiveAfterTransitionComplete` 与 `activeStates()` 各自遍历一次整棵树。

### 2.4 嵌套层级是乘法放大器

| 控制器层深 | ns/op | B/op |
|---|---|---|
| 1 | 327 | 1608 |
| 2 | 776 | 3216 |
| 4 | 1542 | 6432 |
| 8 | 2585 | 12864 |

**完全线性**。每多一层控制器，每帧就多一整套完整的事件处理流程（`progress()` 先递归驱动子控，再触发自身转移）。

### 2.5 桥接层的固定开销

| 项 | ns/op | B/op |
|---|---|---|
| `runBlocking { sink++ }`（空块） | 34 | 120 |
| 完整 `processEventBlocking`（1 状态，无转移） | 231 | 1288 |

协程桥接层本身只占约 **15%** 的时间，但**每次事件固定分配约 1.3 KB** —— 这是纯脚手架成本。

### 2.5b 剩余 1608 B 的逐段归因

见 `KStateMachineAllocationAttributionTest`。方法：构造一串「只比上一档多做一件事」的状态机，
相邻两档的 B/op 差值就是那一件事的成本（所有档位走同一个 `processEventBlocking` 入口，公共部分抵消）。

**归因 A：把一个事件从「被忽略」加到「真发生转移」**

| 档位 | ns/op | B/op | 增量 |
|---|---|---|---|
| 0. 基线：单状态、无转移、事件 IGNORED | 282 | **1360** | — |
| 1. `runBlocking{ withContext{} }` 空转（独立基线） | 39 | 120 | — |
| 2. + EventMatcher 命中 + policy + `noTransition` | 291 | 1600 | +240 B |
| 3. + `TargetState`/`TransitionParams` + 通知派发 | 464 | 2320 | +720 B |
| 4. + 真实转移（`exit`/`enter` + 路径计算） | 666 | 2976 | +656 B |

**结论：1608 B 中，60%（约 960 B）花在「求值一条不成立的转移」上，只有 27%（约 420 B）用于真正改变状态。**

**归因 F：残差的最大单一来源 —— `recursiveAfterTransitionComplete`**

这是每帧转移后无条件执行的整树遍历，而它的默认实现是空函数。

| 兄弟状态数 | ns/op | B/op |
|---|---|---|
| 1 | 113 | 648 |
| 50 | 1820 | 9272 |
| 150 | 5496 | **26872** |

**每个子状态 36.1 ns / 176 B。** 对比归因 B 的每次转移增量 49.9 ns / 208 B：
**`recursiveAfterTransitionComplete` 一项就解释了其中 85%。**
150 个兄弟状态时，它独自贡献 26.9 KB（占该次转移 29.1 KB 的 **92%**）。

> ⚠️ **机制更正**：这里最初把这 176 B 说成「调用空 suspend 函数本身的开销」——**这是错的**。
> 空函数不分配，JIT 也会内联它。真正的机制见下面的归因 G/H。

**归因 G/H：176 B 到底是什么 —— 空 suspend 调用不分配，不可内联的虚 suspend 调用才分配**

| 档位（各遍历 150 个子状态） | ns/op | B/op |
|---|---|---|
| `runBlocking{}` 空基线 | 41 | 120 |
| + 非 suspend `for` 循环 150 次 | 65 | 120 |
| + `Set.forEach` 150 次（非 suspend lambda） | 213 | 120 |
| + 150 次空 `suspend fun (param: Any)` | 40 | **128** |
| + 150 次空 `suspend fun (param: Any?)` | 40 | **128** |
| + 150 次空 `suspend fun ()` | 40 | **120** |
| 非 suspend：`for` 遍历 150 个子状态 + 普通方法 | 29 | **32** |
| suspend：隔离调用 `recursiveAfterTransitionComplete`（单态接收者） | 63 | **296** |
| **suspend：真实转移流程里的同一次遍历** | — | **33944 → 约 160 B/子状态** |

**结论**：

1. **150 次空 suspend 调用总共只分配 8 B** —— 完全符合预期，JIT 把静态可解析的调用内联掉了。
   你最初的质疑是对的。
2. **同一个 `recursiveAfterTransitionComplete`，隔离测量只花 296 B（≈2 B/子状态），
   进了真实转移流程却要 ≈160 B/子状态** —— 差了 80 倍。
3. 差别在**调用点的多态性**：隔离测量时接收者只有 `DefaultState` 一种类型，
   JIT 能去虚化 + 内联 + 把整趟遍历优化掉；真实树里 `InternalState` 有多种子类，
   虚调用点变成多态/巨态（megamorphic），**suspend 函数无法内联**，
   于是每次迭代都必须为一个 continuation 帧分配对象。

所以 176 B/子状态的准确表述是：
**「一次无法内联的虚 suspend 调用，每迭代强制分配一个 continuation 帧」** ——
不是「遍历」本身（非 suspend 遍历只要 32 B / 150 个），也不是「空函数」本身。

**这也解释了为什么这个成本看起来"白花"**：默认实现什么都不做，
但引擎无法在编译期证明所有子类都不覆写它，只能在运行时为每个节点分配一个帧交给一个空函数。

**归因 B：全树遍历 vs 子树宽度**

| 兄弟状态数 | ns/op | B/op |
|---|---|---|
| 1 | 684 | 2952 |
| 10 | 1108 | 4824 |
| 50 | 2995 | 13144 |
| 150 | 8117 | 33944 |

每个不参与转移的兄弟状态仍然贡献 **49.9 ns / 208 B**；150 个兄弟时 **91% 的分配来自全树遍历**。

**归因 D：`activeStates()` 的实际代价（优化 3.4 的价值）**

| 兄弟状态数 | 无监听器 | 有机器级监听器 | 增量 |
|---|---|---|---|
| 1 | 2840 B | 3432 B | +592 B |
| 50 | 11536 B | 12128 B | +592 B |
| 150 | 29136 B | 29728 B | +592 B |

增量是**恒定的 592 B**，不随宽度增长 —— 因为 `GetActiveStatesVisitor` 只走活跃路径。
所以优化 3.4 省下的是固定 592 B/转移，而 **O(宽度) 的部分全部来自 `recursiveAfterTransitionComplete`**。

**归因 C：Spark-Core 包装层的成本**

| 项 | ns/op | B/op |
|---|---|---|
| 裸状态机 `processEventBlocking` 往返转移 | 670 | 2928 |
| `StateGraphController.progress()` 往返转移 | 990 | 3696 |
| **包装层净增** | **+320 ns** | **+768 B** |

### 2.6 其他单点测量

| 项 | ns/op | B/op | 备注 |
|---|---|---|---|
| `activeStates()` | 50–57 | 272 | 与状态数无关（只走活跃路径） |
| 完整 `AnimInstance.state` 表达式 | 94 | 376 | 含 `valueOf` 反射查表 + `uppercase()` |
| `startBlocking()` + `stopBlocking()` 一轮 | 1483 | 6496 | 每次进出状态都要重建协程 Continuation |
| 条件求值（hub 上 1→64 条转移） | 335→507 | 1608 | 约 2.7 ns / 条，扫描成本可接受 |

---

## 3. 落地优化

### 3.1 `findUniqueResolvedTransition` 单遍扫描

`state/InternalState.kt`

原实现每次事件分配 3 个中间 List + N 个 Pair：

```kotlin
findTransitionsByEvent(event)          // filter → 1 个 List
    .map { it to it.produceTargetStateDirection(policy) }   // 1 个 List + N 个 Pair
    .filter { it.second !is NoTransition }                   // 1 个 List
```

改为单遍循环，并把 direction 求值结果复用（原实现每个匹配转移只求值一次，新实现保持一致）：

```kotlin
if (transitions.isEmpty()) return null   // 快路径：无转移的状态零分配
var resolved: ResolvedTransition<E>? = null
for (transition in transitions) {
    if (!transition.isMatchingEvent(eventAndArgument.event)) continue
    val direction = transition.produceTargetStateDirection(policy)
    if (direction is NoTransition) continue
    // 多转移匹配仍抛异常；doNotThrowOnMultiple 时短路
}
```

### 3.2 祖先链行走快路径

`state/BaseStateImpl.kt`

事件解析是**逐级向父级冒泡**的，每一层都分配 4 个集合。
对于「自身无转移、无子状态」的常见情况（叶子状态、扁平图的节点），直接返回 `null`：

```kotlin
if (transitions.isEmpty() && (states.isEmpty() || machine.creationArguments.doNotThrowOnMultipleTransitionsMatch))
    return null
```

### 3.3 监听器为空时跳过防御性拷贝

`state/StateListener.kt`、`statemachine/StateMachineListener.kt`、`transition/TransitionListener.kt`

`listeners.toList()` 在**无监听器时也会分配一个空 List**，而机器级监听几乎总是空的：

```kotlin
val snapshot = machineListeners.takeIf { it.isNotEmpty() }?.toList() ?: return
```

### 3.4 无监听器时跳过 `activeStates()` 全树遍历

`statemachine/StateMachineImpl.kt`

`activeStates()` 只为 `onComplete` 通知服务。没有人监听时整趟遍历都是白做的：

```kotlin
if (_machineListeners.isNotEmpty() || transition.listeners.isNotEmpty()) {
    val activeStates = activeStates()
    transition.transitionNotify { onComplete(activeStates, transitionParams) }
    machineNotify { onTransitionComplete(activeStates, transitionParams) }
}
```

Spark-Core 的 `StateGraphController` 正是「无机器级监听」的典型场景（只用 `onEntry`/`onExit`），
这条优化直接命中它的热路径。

---

## 4. 优化前后对比

| 指标 | 优化前 | 优化后 | 变化 |
|---|---|---|---|
| `progress()` 无转移 — B/op | 2128 | **1608** | **−24%** |
| `progress()` 无转移 — ns/op | ~370 | ~331 | −11% |
| `processEventBlocking` — B/op | 1904 | **1288** | **−32%** |
| `processEventBlocking` — ns/op | ~290 | ~231 | −20% |
| `triggerEvent("miss")` — B/op | 2128 | **1608** | **−24%** |
| 转移一帧 — B/op | 4080 | **3696** | −9% |
| 嵌套 8 层 — B/op | 17024 | **12864** | **−24%** |
| `depth=8` 自转移 — B/op | 7272 | **6080** | **−16%** |
| `depth=16` 自转移 — B/op | 12472 | **10432** | **−16%** |

优化后 `gradlew test` 全量通过：120 项测试，0 失败，0 跳过。

| 测试套件 | 数量 |
|---|---|
| `StateGraphControllerTest`（原有行为测试） | 35 |
| `KStateMachinePerfTest`（新增基准，只打印不断言） | 15 |
| `KStateMachineOptimizationGuardTest`（新增语义守卫） | 17 |
| 其余既有测试（粒子 / 物理） | 53 |

**仍然存在的成本**（有意保留，属结构性设计）：

- **`recursiveAfterTransitionComplete` 的整树遍历 —— 这是当前最大的单一残差**：
  每子状态 176 B，150 个兄弟状态时独占该次转移的 92%（详见 2.5b 归因 F）
- 每次转移的固定 592 B（`activeStates()`，仅在有监听器时）
- 嵌套层级线性叠加 —— 每层控制器每帧一整套事件流程
- `AnimInstance.state` 每次读取 94 ns / 376 B

### 4.1 还能继续压吗？—— 残差结构与下一步

按 2.5b 的归因，当前每次转移约 3.0 KB 的构成是：

| 部分 | 占比 | 能否继续优化 |
|---|---|---|
| `recursiveAfterTransitionComplete` 整树遍历 | 随宽度增长，宽树下 >90% | ✅ **可以，且是最大头** |
| `TargetState` + `TransitionParams` + 通知派发 | ~720 B | ⚠️ 可小幅压缩（`TargetState` 可用单元素 `Set` 常量池） |
| `activeStates()` | 592 B（固定） | ✅ 已用 3.4 在无监听器时消除 |
| 真实 `exit`/`enter` | ~656 B | ❌ 必要工作 |
| 求值一条不成立的转移 | ~240 B | ❌ 必要工作 |
| 脚手架（`EventAndArgument`/`Step1Result`/协程帧） | ~240 B | ⚠️ 可压缩，收益小 |

#### 下一步：消除 `recursiveAfterTransitionComplete` 的整树遍历

**问题本质**（已由归因 G/H 确认）：`BaseStateImpl` 的默认实现是空函数，
但引擎无法在编译期证明所有子类都不覆写它，于是**每次转移**都要为每个子状态
分配一个 continuation 帧、交给一个空函数。实测 **160–176 B / 36 ns 每子状态**。

**为什么不能靠 JIT 自动解决**：`InternalState` 是多态接收者，虚 suspend 调用点
多态化后无法内联 —— 这不是代码写得不好，是**设计与 JIT 的固有冲突**。

**可行方案**：给 `InternalState` 增加能力声明，默认关闭，只有真正覆写了
`recursiveAfterTransitionComplete` 的状态才打开：

```kotlin
abstract class InternalState : IState, InternalNode {
    /**
     * 该状态是否需要「转移完成」回调。
     * 只有覆写了 [recursiveAfterTransitionComplete] 的状态才应返回 true。
     * 默认 false —— 让引擎可以整枝剪掉这趟遍历。
     */
    internal open val needsAfterTransitionComplete: Boolean get() = false
}
```

然后在 `BaseStateImpl` 中维护一个**子树聚合标志**（新增子状态时更新），
递归时遇到 `false` 直接返回：

```kotlin
override suspend fun recursiveAfterTransitionComplete(transitionParams: TransitionParams<*>) {
    if (!subtreeNeedsAfterComplete) return          // 整枝剪枝
    data.states.forEachState { it.recursiveAfterTransitionComplete(transitionParams) }
}
```

**更彻底的替代方案**（如不接受新 API）：把「转移完成」回调从**每节点虚 suspend 方法**
改成**注册制**——需要回调的状态在构造时注册到一个扁平列表，转移后只遍历该列表。
这样遍历长度与树规模解耦，且天然避开多态虚 suspend 调用点。
代价是 `recursiveAfterTransitionComplete` 这个公开扩展点的语义变更，需要废弃期。

**风险与前置条件（必须先确认再动手）**：

1. **这是公开扩展点，且 `InternalState` / `DefaultState` 都是 public 的** ——
   生态外的第三方子类可以覆写 `recursiveAfterTransitionComplete`，无法排除。
   → **必须采用「默认 `true`、显式关闭」的保守版本**，任何第三方子类自动维持现有行为，零破坏。
   > 完整子类审计（含 ARMS-Core）见
   > [`kstatemachine_after_complete_audit.md`](kstatemachine_after_complete_audit.md)。
2. **审计结论**：本生态（Spark-Core + ARMS-Core）内真实覆写只有 **2 处**，
   均在 vendored 库内部（`UndoState`、`DefaultHistoryState`），且都是**伪状态**
   （`BasePseudoState.addState` 直接抛异常 → 必然无子状态 → 剪枝不影响其钩子执行）。
   Spark-Core **0 个子类**；ARMS-Core **纯 Java、0 个子类、0 个 internal API 使用**。
3. **测试缺口**：`UndoState` / `DefaultHistoryState` 目前**完全没有测试覆盖**
   （`src/test/` 下 `isUndoEnabled` / `undo(` / `historyState` / `HistoryType` 零命中）。
   **必须先补这两个语义测试 + 一条「剪枝不误伤」守卫测试，再动优化。**

**收益估算**：宽树（100+ 状态）场景下可消除该次转移 **90% 以上**的分配。
按 200 实体、每 tick 一次转移、每树 150 个状态估算，约 **5.3 MB/tick → 接近 0**。

> 在此之前，**不建议**再对 `EventAndArgument` / `Step1Result` / `TargetState` 这些
> 固定小对象做微优化 —— 它们合计不到 250 B，改动收益远小于上面这一项，且更容易踩到语义坑。

---

## 5. 推荐后续动作（按收益排序）

### 5.1 ⭐ 缓存 `AnimInstance.state`

```kotlin
val state get() = AnimState.valueOf(lifecycleStateMachine.activeStates().firstOrNull()?.name?.uppercase() ?: "IDLE")
```

这是 `isFinished` / `isInTransition` / `stepWeight()` 的共同依赖，**每个动画实例每 tick 被读取 5 次**
（物理线程 3 次 + 主线程 2 次）。改为在 `onEntry` 回调里写入一个 `@Volatile` 字段即可把 94 ns/376 B 降到接近 0。

粗算：200 实体 × 8 实例 × 5 次 × 376 B × 20 TPS ≈ **60 MB/s** 的纯垃圾。

> **完整改法、约束条件与验证方案见 [第 6 节](#6-方案细节缓存-animinstancestate)。**

### 5.2 ⭐ 剪枝 `recursiveAfterTransitionComplete` 的整树遍历

每子状态 **160–176 B / 36 ns**，且在**每次转移**后无条件执行 —— 150 兄弟状态下独占该次转移 **92%** 的分配。
机制已通过归因 G/H 定位：**不可内联的多态虚 suspend 调用，每迭代强制分配一个 continuation 帧**
（非 suspend 遍历同一棵树只要 32 B）。

**✅ 子类审计已完成**：全生态只有 2 个覆写点（`UndoState`、`DefaultHistoryState`），
均为伪状态、必然无子状态；Spark-Core 0 个子类；ARMS-Core 纯 Java、0 个子类。
但 `InternalState` 是 public，生态外子类无法排除 → **必须用默认 `true` 的保守方案**。
详见 [`kstatemachine_after_complete_audit.md`](kstatemachine_after_complete_audit.md)。

> ⚠️ 三个被测量或审计否定的早期想法：
> 1. 「把 `recursiveAfterTransitionComplete` 与 `activeStates()` 合并成一趟」—— `activeStates()`
>    的增量是**恒定 592 B**、不随宽度增长（归因 D），合并省不下 O(宽度) 那部分。
> 2. 「这只是空函数调用，JIT 会优化掉」—— 隔离测量确实会（296 B），
>    但真实流程里调用点多态化后 JIT 无能为力（33 944 B）。
> 3. 「`InternalState` 是 `internal`，构成结构性屏障」—— **错**，它是 public，
>    外部子类可以覆写。这条更正直接改变了方案选择（见审计文档 §5）。

### 5.3 减少嵌套层数

`progress()` 的线性叠加说明：**把 4 层控制器拍平成 2 层，每帧成本直接砍半**。
`MultiAnimStateMachine` 的 1:N 广播模式如果能在同一层内表达，比新增一层子控更划算。

### 5.4 事件驱动优先于 auto 转移

`event != null` 走 `node.eventTransitions[type]` 的 O(1) Map 查找；
`event == null` 走 `node.autoTransitions.firstOrNull { condition.check() }` 的线性扫描。
前者在条件多时更省（实测 64 条条件下 317 ns vs 507 ns）。

### 5.5 谨慎使用 `broadcastEvent`

它会递归到所有活跃子控制器，每个都跑一整套 `processEventBlocking`。
高频事件建议只发往真正需要的那一层。

---

## 6. 方案细节：缓存 `AnimInstance.state`

### 6.1 为什么要改

`AnimInstance.state` 是**计算属性**，每次读取都要跑一遍状态机查询：

```kotlin
// AnimInstance.kt:50
val state get() = AnimState.valueOf(lifecycleStateMachine.activeStates().firstOrNull()?.name?.uppercase() ?: "IDLE")
```

实测拆解：

| 表达式 | ns/op | B/op |
|---|---|---|
| `activeStates()` | 50–57 | 272 |
| `+ .firstOrNull()?.name?.uppercase()` | 80 | 376 |
| `+ Enum.valueOf()`（完整表达式） | **94** | **376** |

其中 `activeStates()` 内部是 `GetActiveStatesVisitor` —— 每次新建 `mutableSetOf()`，
`accept(visitor)` 再走 `coroutineAbstraction.withContext`。

### 6.2 谁在读（全量清点）

| 位置 | 线程 | 每 tick 次数 | 读取的属性 |
|---|---|---|---|
| `AnimLayer.physicsTick` L95 `removeIf { it.state == AnimState.IDLE }` | 物理线程 | 1 | `state` |
| `AnimInstance.physTick` L242 `if (isInTransition)` | 物理线程 | 1 | `state` |
| `AnimInstance.stepWeight` L199/209 `when (state)` | 物理线程 | 1 | `state` |
| `AnimStateMachine.onCheckTransition` L40 `inst.isFinished` | 主线程 | 1 | `state` |
| `MultiAnimStateMachine.onCheckTransition` L80 `inst.isFinished` | 主线程 | 1 | `state` |

**每个动画实例每 tick 5 次**，横跨物理线程与主线程。

> `AnimApplier.entityTick`（主线程，`EntityTickEvent.Post`）调 `animController.tick()`；
> `AnimApplier.physTick`（物理线程，`PhysicsEntityTickEvent`）调 `animController.physTick()`。
> 两个线程都会读 —— 这决定了下面必须用 `@Volatile`。

### 6.3 改法

**核心思路：把「查询」变成「状态机在进入每个状态时主动写入的缓存」。**

`AnimInstance` 的 `lifecycleStateMachine` 只有 4 个状态（idle / enter / work / exit）且**无分支**，
状态转移完全由代码驱动 —— 非常适合缓存。而且对 `AnimLayer` 的移除判断来说，
缓存带来的语义甚至更准确：旧写法依赖 `name.uppercase()` 拼字符串再 `valueOf`，
一旦有人把 state 名字改成 `Idle` 或加前缀，`valueOf` 会**直接抛 `IllegalArgumentException`**。

**改动 1 —— `AnimInstance.kt`：用缓存字段替换计算属性**

```kotlin
/**
 * 当前生命周期状态缓存。
 *
 * 由 [lifecycleStateMachine] 各状态的 onEntry 回调写入。
 * 读取点在物理线程（AnimLayer/physTick/stepWeight）与主线程（onCheckTransition）各有一半，
 * 故标记 @Volatile。
 */
@Volatile
private var cachedState: AnimState = AnimState.IDLE

val state get() = cachedState

/** 改为 when，避免每次读取都新建一个 List */
val isInTransition get() = when (cachedState) {
    AnimState.ENTER, AnimState.EXIT -> true
    else -> false
}

val isFinished get() = cachedState == AnimState.IDLE
```

**改动 2 —— 在 4 个状态的 `onEntry` 里写入**

```kotlin
private val lifecycleStateMachine by lazy {
    createStdLibStateMachine {
        val idle = initialState("idle")
        val enter = state("enter")
        val work = state("work")
        val exit = state("exit")

        idle.apply {
            onEntry { cachedState = AnimState.IDLE; refresh() }
            transition<AnimStateEvent.Start> { targetState = enter }
        }
        enter.apply {
            onEntry { cachedState = AnimState.ENTER; /* ...原有逻辑不变... */ }
            // ...
        }
        work.apply {
            onEntry { cachedState = AnimState.WORK }
            // ...
        }
        exit.apply {
            onEntry { cachedState = AnimState.EXIT; /* ...原有逻辑不变... */ }
            // ...
        }
    }
}
```

**关键约束：写入必须在「本层进入时同步发生」。** 三条理由：

1. 本层 `onEntry` 在 `doEnter` 内、`recursiveEnterInitialStates` 之前触发 → 父先于子，缓存不会读到旧值。
2. 从 `exit → idle` 结束时，本层 `onEntry` 一定在「`exit()` 返回 / `isFinished` 被读」之前跑完。
3. **不能被 `machineNotify` 的协程调度推迟** —— 这一点依赖本次已落地的优化 3.3：
   该状态机**没有任何机器级监听器**，所以 `machineNotify` 是空操作、`processEventBlocking` 全程同步。
   → 这条依赖必须写进代码注释，并在守卫测试里锁住。

**改动 3（可选清理）—— 复用已缓存的 tick 数**

`stepWeight()` 里 `inTransitionTick` / `outTransitionTick` 每次读取都会重算
`(inTransitionTime * tps)`（`tps` 还要走 `holder.animLevel` 判空）。而 `onEntry` 里已经把值存进
`transitionTick` 了，可以顺手缓存下来。收益远小于改动 1，可放在同一批里做。

### 6.4 预期收益

| 项 | 改前 | 改后 |
|---|---|---|
| 单次 `state` 读取 | 94 ns / 376 B | ~1 ns / 0 B |
| 每实例每 tick（5 次） | 470 ns / 1880 B | ~5 ns / 0 B |
| 200 实体 × 8 实例 | **752 µs / tick，3.0 MB / tick** | 接近 0 |

即 **约 60 MB/s 的纯垃圾消失**，同时省下约 1.5% 的 tick 预算。
（3.0 MB/tick × 20 TPS = 60 MB/s —— 足以让 young GC 从「偶尔」变成「频繁」。）

### 6.5 验证方案

**必须新增的守卫测试** `AnimInstanceStateCacheTest`：

1. **一对一路径覆盖**：对 idle / enter / work / exit 每个状态，
   用 `instance.state` 与「原始查询表达式」逐一对拍，断言缓存永不失同步。
   这条是**关键回归守卫** —— 一旦有人新增状态却忘了写 `cachedState`，立即红。
2. **跨状态序列**：`enter() → stepWeight()×N → work → exit() → stepWeight()×N → idle`，
   在每一步断言 `state`，并断言 `isFinished` / `isInTransition` 随之正确翻转。
3. **初始值**：构造后未 `enter()` 时应为 `IDLE`，且不触发 `lifecycleStateMachine` 的惰性初始化。
4. **主线程可见性**：物理线程推进后，从另一线程读取应看到最新值（`@Volatile` 生效）。

**已有的基准测试**可直接对比 `activeStates()` 与完整表达式的开销（本报告 2.6 节的 94 ns / 376 B 即基线）。

**回归**：`gradlew test` 全量必须保持 120 项通过。

### 6.6 不建议一起做的

- **不要**把 `lifecycleStateMachine` 整体换成普通枚举/字段。它承载了
  `onEntry` 里的 `playAnimation` / `triggerEvent(AnimEvent.Start)` 等副作用，
  拆掉等于重写动画生命周期，风险远大于收益。
- **不要**移除 `AnimInstance.physTick` 里的 `processEventBlocking`。它们每 tick 固定分配约 1.3 KB，
  看着刺眼，但只在 `Loop.ONCE` 播完 / 过渡结束时触发，不是每帧路径。要优化应该改
  `TransitionFinish` 的判定方式，属于独立议题。

---

## 7. 复现

```bash
# 全部基准 + 行为守卫
gradlew test --tests "cn.solarmoon.spark_core.state_machine.perf.*"

# 只跑基准
gradlew test --tests "cn.solarmoon.spark_core.state_machine.perf.KStateMachinePerfTest"

# 分配归因（逐段消融）
gradlew test --tests "cn.solarmoon.spark_core.state_machine.perf.KStateMachineAllocationAttributionTest"

# 只跑优化语义守卫
gradlew test --tests "cn.solarmoon.spark_core.state_machine.perf.KStateMachineOptimizationGuardTest"
```

基准测试**只打印不断言**（不会因机器性能差异导致 CI 红）；守卫测试**断言行为**（必须通过）。
