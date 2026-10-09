# `recursiveAfterTransitionComplete` 子类审计

> **审计目的**：确认能否安全剪枝「转移完成后遍历整棵状态树」这一行为
> （详见 [`kstatemachine_performance_analysis.md`](kstatemachine_performance_analysis.md) 第 4.1 节的优化方案）。
>
> **审计结论**：✅ **本生态内可以安全剪枝，无依赖**。
> 全生态只有 **2 个**覆写点，且都位于**必然没有子状态**的伪状态类中。
>
> ⚠️ **但 `InternalState` / `DefaultState` 是 public 的**，生态外的第三方子类无法排除 ——
> 因此落地必须采用**默认 `true`** 的保守方案（见 §6.2），不能激进剪枝。
>
> 📌 本文件包含一次**自我更正**（初版误判 `InternalState` 为 `internal`），详见 §5。

---

## 1. 审计范围

| 范围 | 说明 |
|---|---|
| Spark-Core 主源码 | `src/main/kotlin/`（含 vendored KStateMachine） |
| Spark-Core 测试 | `src/test/` |
| ARMS-Core | `D:\Files\Project_MinecraftMods\ARMS-Core`（通过 Gradle `includeBuild` 组合构建依赖 Spark-Core） |

**审计方法**：全量正则扫描 + 逐文件人工确认，覆盖 `extends` / `implements` / `override` 三类语法，
不限文件扩展名（`.kt` / `.java`）。

---

## 2. 覆写点全量清点

全生态只有 3 处出现 `recursiveAfterTransitionComplete`（1 处声明 + 2 处覆写）：

| 文件 | 行 | 类型 | 角色 |
|---|---|---|---|
| `state/InternalState.kt` | 72 | `abstract suspend fun` | 抽象声明 |
| `state/BaseStateImpl.kt` | 293 | `override` | 默认实现：`data.states.forEachState { it.recursiveAfterTransitionComplete(...) }` |
| `state/pseudo/UndoState.kt` | 21 | `override` | **真实覆写** ⚠️ |
| `state/pseudo/DefaultHistoryState.kt` | 40 | `override` | **真实覆写** ⚠️ |

**真实覆写只有 2 个，全部在 vendored 库内部的 `state/pseudo/` 包。**

---

## 3. 两个覆写点的可达性分析

两个覆写都继承自 `BasePseudoState`，而它有三重封闭保证：

```kotlin
// state/pseudo/BasePseudoState.kt
open class BasePseudoState(name: String?) : BaseStateImpl(name, ChildMode.EXCLUSIVE), PseudoState {
    override suspend fun doEnter(...) = internalError()   // 伪状态不可进入
    override suspend fun doExit(...)  = internalError()   // 伪状态不可退出
    override fun <S : IState> addState(state: S) =
        throw UnsupportedOperationException("PseudoState $this can not have child states")  // ⭐ 关键
    override fun <E : Event> addTransition(...) =
        throw UnsupportedOperationException("PseudoState $this can not have transitions")
}
```

**⭐ 关键点：`addState` 直接抛异常 → 伪状态永远没有子状态 → `data.states` 恒为空集合。**

这意味着：

1. 递归遍历在伪状态处**天然短路**（空集合 → 不递归）。
2. 剪枝逻辑不会漏掉它们 —— 剪枝只影响「是否向下递归」，不影响「当前节点是否执行钩子」。

### 各自的实际使用情况

| 状态 | 由谁创建 | 启用条件 | Spark-Core / ARMS-Core 是否使用 |
|---|---|---|---|
| `UndoState` | `StateMachineImpl.init` | **仅当** `CreationArguments.isUndoEnabled = true`（**默认 false**） | ❌ 两仓库均未启用 undo |
| `DefaultHistoryState` | 用户显式调 `historyState(...)` | 需要显式调用 | ❌ 全仓库 0 处调用 |

实测扫描（Spark-Core 主源码）：

```
historyState|choiceState|finalState|initialFinalState|isUndoEnabled|undoState|DefaultHistoryState
→ 仅命中 initialChoiceState（EntityBaseUseAnimStateMachine ×2、PlayerBaseAnimStateMachine ×3）
→ historyState: 0 处    isUndoEnabled: 0 处
```

注意 `choiceState`（选择伪状态）**不覆写** `recursiveAfterTransitionComplete`，不在影响面内。

---

## 4. 子类清点：谁继承了状态类

### 4.1 Spark-Core

| 扫描项 | 结果 |
|---|---|
| `class X : DefaultState / DefaultFinalState / DefaultDataState / BaseStateImpl / InternalState / BasePseudoState / DefaultHistoryState / UndoState` | **0 处** |
| 覆写 `InternalState` 契约成员（`recursive*` / `doEnter` / `doExit` / `afterChildFinished` / `onParentCurrentStateChanged` / `getCurrentStates` / `cleanup` / `setParent`） | **0 处** |

**Spark-Core 完全不触碰 `InternalState` 的契约，只消费公开 DSL**
（`state()` / `initialState()` / `choiceState()` / `transitionConditionally()` / 监听器）。

`StateGraphController` 用的正是公开工厂函数，创建的是库自带的 `DefaultState`：

```kotlin
// StateGraphController.kt:88
val state = (if (id == graph.initialNode.name) initialState(graph.initialNode.name) else state(id)).apply { ... }
```

### 4.2 ARMS-Core

**ARMS-Core 是纯 Java 项目**（61 个 `.java`，**0 个 `.kt`**）。

| 扫描项 | 结果 |
|---|---|
| `extends DefaultState / DefaultFinalState / BaseStateImpl / InternalState / BasePseudoState / ...` | **0 处** |
| `implements ... IState / PseudoState / DataState / MutableDataState / HistoryState / RedirectPseudoState` | **0 处** |
| 使用 `InternalStateMachine` / `recursive*` / `openListenersMutationSection` / `GetActiveStatesVisitor` | **0 处** |
| `build.gradle` / `repositories.gradle` 中出现 `kstatemachine` | **0 处** |

ARMS-Core 与状态机相关的代码全部走公开 API：

| 文件 | 用法 |
|---|---|
| `common/control/state/MechaLogicStateMachine.java` | `extends StateGraphController`，只覆写 `progress` / `onTriggered` / `onEntry` / `onExit` |
| `common/control/state/graph/SimpleVariableCondition.java` | `implements StateCondition` |
| `common/control/state/graph/MechaStateActions.java` | `implements StateAction` |
| `common/control/state/preset/{GaitSubGraphs,PostureLogicGraphs,VerticalSubGraphs}.java` | 只构造 `StateMachineGraph` / `StateNode` / `StateTransition` 数据对象 |

**`StateCondition` 与 `StateAction` 都与「状态节点」正交** —— 实现它们不会产生新的状态类，
自然不会涉及 `recursiveAfterTransitionComplete`。

---

## 5. 可见性分析：屏障比想象中弱

> ⚠️ **本节是对初版审计的更正。** 初版声称「`InternalState` 是 `internal`，构成结构性屏障」——
> **这是错的**。`InternalState` 是 **public** 的。

实际可见性（`state/InternalState.kt`）：

```kotlin
internal interface InternalNode { ... }                    // internal

abstract class InternalState : IState, InternalNode {      // ⚠️ PUBLIC（无修饰符）
    abstract override val internalParent: InternalState?   // public
    internal abstract fun setParent(parent: InternalState)
    internal abstract fun getCurrentStates(): List<InternalState>
    internal abstract suspend fun doEnter(...)
    internal abstract suspend fun recursiveAfterTransitionComplete(...)   // internal 成员
    ...
}
```

| 事实 | 后果 |
|---|---|
| `InternalState` 是 **public** | **模块外代码可以声明它的子类** |
| `recursiveAfterTransitionComplete` 是 `internal abstract` | 外部子类**可以覆写**它 —— 覆写一个 `internal` 成员是允许的 |
| 大量成员是 `internal abstract` | 外部子类**很难完整实现**（无法覆写自己看不见的抽象成员）→ 实践中基本只能继承现成的 `DefaultState` / `BaseStateImpl` |
| `DefaultState` / `DefaultFinalState` / `BasePseudoState` / `DefaultHistoryState` 都是 **public open** | **外部代码可以继承它们并覆写 `recursiveAfterTransitionComplete`** ← 真实风险点 |

**所以初版的「结构性屏障」不成立。** 真实情况是：

- ❌ 不能断言「下游代码无法覆写」
- ✅ 只能说「**本生态内**（Spark-Core + ARMS-Core）确实没有任何覆写」
- ⚠️ 对**生态外**的消费者（其他 mod、其他 KStateMachine 用户）无法保证

**这一点直接决定了方案选择**：不能采用「默认 `false`」的激进剪枝，
必须用**默认 `true`** 的保守版本（见 §6.2 方案 A），否则会静默破坏外部子类的行为。

---

## 6. 审计结论与建议

### 6.1 结论

**对本生态：✅ 可以剪枝。对生态外：⚠️ 必须提供兼容路径。**

依据（按强度排序）：

1. **本生态覆写点可枚举**：Spark-Core + ARMS-Core 全量扫描，真实覆写只有 2 处，
   都在 vendored 库内部的 `state/pseudo/` 包（`UndoState`、`DefaultHistoryState`）
2. **两者都是伪状态**，`addState` 抛异常 → **必然无子状态** → 剪枝不影响其钩子执行
3. **Spark-Core 0 个子类**、**0 个 `InternalState` 契约覆写**
4. **ARMS-Core 纯 Java**（0 个 `.kt`）、**0 个子类**、**0 个 internal API 使用**
5. ⚠️ **但 `InternalState` / `DefaultState` 是 public**，生态外第三方子类不可排除
   → 必须默认保持行为不变

### 6.2 落地建议（按风险从低到高）

**方案 A（推荐，零 API 风险）—— 保守默认 + 显式开启**

```kotlin
abstract class InternalState : IState, InternalNode {
    /**
     * 该子树是否需要「转移完成」回调。
     * 默认 true 以保持向后兼容；已知无覆写的子树可置为 false 以剪枝整趟遍历。
     */
    internal open val needsAfterTransitionComplete: Boolean get() = true
}
```

由**确认无覆写**的子树（本生态内的全部状态）返回 `false`；
`UndoState` / `DefaultHistoryState` 保持继承默认的 `true`。

**为什么默认 `true` 是关键**：任何生态外的第三方子类（无论是否覆写）都自动维持今天的行为，
**零破坏**。收益只对显式关闭的子树生效 —— 而本生态可以全部关闭。

**已落地（2026-10-10）**：采用方案 A 的剪枝思路，但**不要求手工声明**——`BaseStateImpl` 通过**类层次探测**
自动判定子树内是否有覆写者（沿 `javaClass` 上溯到 `BaseStateImpl`，检查类是否声明了
`recursiveAfterTransitionComplete`；Kotlin 的 internal 成员带模块名后缀，故按名称前缀匹配），
结果按状态缓存、`setParent` 时沿祖先链失效；`InternalState` 的默认值仍为 `true`。
这样生态外子类即便覆写也不会被静默剪掉。实测 150 个兄弟状态的一次转移：
**29008 → 2608 B/op**、**7326 → 1164 ns**，且不再随树宽增长。
守卫：新增 `KStateMachineAfterCompletePruningGuardTest`（4 项：无覆写者剪枝、undo 例外、
深层覆写者沿祖先链传播、宽树分配量上限）+ 既有 `PseudoStateSemanticsTest`（16 项语义）。

**方案 B（更彻底）—— 注册制**

把回调从「每节点虚 suspend 方法」改为「注册到扁平列表，转移后只遍历该列表」：

```kotlin
// 仅 UndoState / DefaultHistoryState 会注册
private val afterCompleteHooks = mutableListOf<AfterCompleteHook>()
```

遍历长度与树规模**完全解耦**，且天然避开多态虚 suspend 调用点（正是 176 B/子状态的根因）。
默认列表为空 → 第三方子类的行为同样需要**显式注册**才能保留，
因此**必须配合废弃期**：让现有覆写点在编译期报错/警告，而不是运行时静默失效。

**方案 C（不推荐）—— 直接删除整树遍历**

会破坏 UndoState / DefaultHistoryState 的语义，虽然二者当前都没被使用，
但属于**功能性回归**，且一旦将来有人启用就静默失效。

### 6.3 测试安全网（✅ 已建立）

覆盖文件：[`src/test/kotlin/cn/solarmoon/spark_core/state_machine/pseudo/PseudoStateSemanticsTest.kt`](../src/test/kotlin/cn/solarmoon/spark_core/state_machine/pseudo/PseudoStateSemanticsTest.kt)
（16 项，全部通过）

| 分组 | 断言 |
|---|---|
| **UndoState** | undo 回到上一状态、连续 undo 逐级回退、无历史时被忽略且不抛异常、未启用 undo 时无该状态、不自我记录 `WrappedEvent`、事件来自倒数第二条记录 |
| **DefaultHistoryState (SHALLOW)** | 无子状态、defaultState 回落、记录父状态最后子状态、停止时行为 |
| **DefaultHistoryState (DEEP)** | 记录下探到最深活跃叶、停止时行为 |
| **剪枝安全性核心** | ⭐ **`每次转移后 UndoState 钩子都被调用`**、⭐ **`每次转移后 DefaultHistoryState 钩子都被调用`** |
| **结构性前提** | 伪状态禁止添加子状态 |

> ⭐ 两项是剪枝优化的**直接看门测试**：钩子一旦被漏掉，
> 回退栈不再增长、`storedState` 不再更新，测试立刻失败。

### 6.4 测试过程中发现的真实语义（重要）

写测试时发现三个**与直觉不符**的实际行为。它们都已被测试固定，
**任何剪枝改动都必须保持这些行为不变**：

| # | 行为 | 影响 |
|---|---|---|
| 1 | `UndoState.makeWrappedEvent()` 取 `stack[size-2]`（**倒数第二条**），所以在 `start(a) → a-Go->b` 后 undo，包装内是**启动事件** `StartEventImpl`，不是刚才的 `Go` | 源码 `TransitionDirection.kt:113` 已有 `fixme` 注释确认这是已知问题 |
| 2 | 回退栈不足（`size < 2`）时 `undo()` 返回 **`IGNORED`**（不是 `PROCESSED`）—— 因为 `popTargetStates()` 返回空集合，不产生转移 | 调用方若用返回值判断"是否发生回退"必须知道这点 |
| 3 | `onStopped()` 清空历史后，**停止转移的 `recursiveAfterTransitionComplete` 会立刻重新写入** —— DEEP 模式下停止后 `storedState` 变成 `level3a` 而非默认的 `level2` | 意味着 `onStopped` 的清空在当前流程下**实际上是无效的** |

第 3 条本身可能就是 `DefaultHistoryState` 的一个 bug：停止本应让历史归零，
但清理时机早于最后一次钩子回调。**这进一步说明为什么不能贸然剪枝** ——
剪枝会改变钩子调用时机，可能把这类已有的时序依赖彻底打乱。

---

## 7. 复现审计

```powershell
# Spark-Core：所有覆写点
Select-String -Path "src\main\kotlin\ru\nsk\**\*.kt" -Pattern "override.*recursiveAfterTransitionComplete"

# Spark-Core：是否存在状态子类
Select-String -Path "src\main\kotlin\**\*.kt" -Pattern ":\s*(DefaultState|BaseStateImpl|InternalState|BasePseudoState)"

# ARMS-Core：是否存在状态子类 / internal API 使用
Select-String -Path "D:\Files\Project_MinecraftMods\ARMS-Core\src\**\*.java" `
  -Pattern "extends\s+(DefaultState|BaseStateImpl|InternalState)|InternalStateMachine|recursive"

# 可见性核对（初版审计的错就在这里）
Select-String -Path "src\main\kotlin\ru\nsk\kstatemachine\state\InternalState.kt" -Pattern "^abstract class InternalState"
```
