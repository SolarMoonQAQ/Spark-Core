# 子状态机事件递归广播改造计划

> **状态**：已完成  
> **创建**：2026-07-16  
> **完成**：2026-07-16  
> **范围**：`cn.solarmoon.spark_core.state_machine.graph.StateGraphController` 及其测试  
> **关联**：[状态机统一计划](./state_machine_unification_plan.md) · [状态变量与多目标动画计划](./state_variable_and_multianim_plan.md)

---

## 1. 背景

`StateGraphController` 当前已支持树形子控制器：

- 构造时由工厂递归创建全部直接 `children`。
- 进入父状态时，根据该节点的 `subGraphs.keys` 重置并激活对应子控制器。
- 退出父状态时，递归退出并清空当前 `activeChildren`。
- `progress()` 先递归推进活跃子控制器，再推进当前控制器的无事件转移。

子图没有嵌入父级 KStateMachine，而是各自拥有独立的 `StateGraphController` 和 KStateMachine 实例。父子可以共享 `StateVariableContainer` 与 `GameplayTagContainer`，但事件目前只会送到调用 `triggerEvent()` 的那个控制器。

因此，下游在根控制器上调用：

```kotlin
root.triggerEvent("dodge")
```

只能匹配根控制器当前节点的 `eventTransitions`，无法触发 gait、vertical 等活跃子图中的同名转移。

ARMS-Core 的 posture + gait + vertical 结构需要一个逻辑事件能够送到整棵活跃状态树。例如：

- `dodge` 由当前 gait 子图响应。
- `jump_start`、`jump_release`、`fly` 由当前 vertical 子图响应。
- `hurt` 未来可以同时让 gait 进入硬直，并让局部动作子图中断攻击。

---

## 2. 现有实现结论

### 2.1 子控制器生命周期

`activeChildren` 只包含当前父状态实际激活的直接子控制器。未激活子图仍可能存在于 `children`，但不会被 `progress()`、`onExit()` 处理。

事件广播必须遍历 `activeChildren`，不能遍历全部 `children`，否则会改变未激活子图的状态，并在下次激活时产生隐藏历史。

### 2.2 动画状态机兼容性

`AnimStateMachine` 和 `MultiAnimStateMachine` 都继承 `StateGraphController`。它们只覆写：

- `onCheckTransition()`：刷新动画完成状态。
- `onExit()`：清理本层动画追踪，再调用父类递归清理子图。

递归广播通过每层已有的 `triggerEvent()` 触发转移，会自然执行这些钩子，无需在两个动画子类重复实现。

### 2.3 ActionEvent 的现有问题

`ActionEvent.targetNode` 当前在找到转移时被赋值为当前 `node`，实际记录的是源节点，而不是目标节点。仓库内暂未发现该字段的外部使用者，但新增广播测试和调试输出前应修正其语义。

---

## 3. 设计决定

### 3.1 新增递归广播，保留单层触发

保留现有 API：

```kotlin
open fun triggerEvent(type: String?): ActionEvent
```

它继续只处理当前控制器，并继续由 `progress()` 使用 `triggerEvent(null)` 推进无事件转移。

新增非空事件广播 API：

```kotlin
open fun broadcastEvent(type: String)
```

第一阶段不加入控制器名称路由、事件消费或通用事件总线。父级优先只规定传播顺序，不使父级独占事件。

### 3.2 广播语义

`broadcastEvent(type)` 采用父级优先的动态树传播语义：

1. 当前控制器先处理事件，再读取其处理后实际活跃的直接子控制器。
2. 处理后仍活跃的每个控制器恰好接收一次事件，顺序为父到子。
3. 父级转移退出的旧子图已从 `activeChildren` 移除，不会收到事件。
4. 父级转移新激活的子图会继续收到同一个事件；事件可用于初始化或推进刚进入的模式。
5. 不消费、不短路；多个并行子图允许响应同一事件。
6. 同层子控制器的先后顺序不属于状态机语义，兄弟子图不得依赖彼此在同一次广播中的副作用。
7. `null` 只表示无事件自动推进，不允许通过广播 API 传入。

这套语义适合层级模式切换：例如 `enter_flight` 可先令 posture 进入飞行模式，再令新激活的 vertical 子图进入 `ascending` 或 `cruising`。同名事件同时影响多个活跃子图仍是广播模式的预期能力，例如 `hurt` 可以同时终止 gait 动作与局部武器动作。

事件名应表达跨状态有效的领域语义。`enter_flight`、`equip_weapon`、`start_swimming` 等可安全传入新子图；`release_jump`、`finish_reload` 等旧状态生命周期事件应使用更具体的命名，避免新模式存在同名转移时发生意外响应。若未来确实出现“只允许一个控制器处理”或“父级转移应屏蔽后代”的需求，再单独设计消费式或短路式 API，不修改本次广播语义。

兄弟子图可以共享 `StateVariableContainer` 和 `GameplayTagContainer`，但共享不代表允许建立同一轮广播内的顺序依赖：

- 转移条件应读取帧首快照、父级已经确定的模式或此前稳定的数据。
- 两个兄弟子图不应在同一轮广播中写入并读取同一个临时结果。
- 两个兄弟子图写入同一个状态键属于状态图设计错误。
- 必须跨子图协调时，由父级集中派生结果，或在下一次 `progress()` 中读取稳定结果。

### 3.3 父级优先的原因

父级首先确定本次事件后的模式与活跃子树，再把同一个事件向下传播，符合上层状态决定下层上下文的层级关系。父级切换时，`onExit()` 会清空旧 `activeChildren`，`onEntry()` 会激活新子图；因此在 `triggerEvent(type)` 之后读取 `activeChildren`，能自然跳过旧子图并将事件交给新子图。

这与 `progress()` 的子级优先自动推进刻意不同：`progress()` 处理每 tick 的被动条件更新，而 `broadcastEvent()` 处理由上层模式切换塑造下层上下文的显式逻辑事件。

---

## 4. 计划实现

目标文件：

```text
src/main/kotlin/cn/solarmoon/spark_core/state_machine/graph/StateGraphController.kt
```

建议实现：

```kotlin
/**
 * 将非空事件从当前控制器向其处理后的活跃子树广播。
 * 顺序：父级优先、深度优先；不消费、不短路。
 */
open fun broadcastEvent(type: String) {
    triggerEvent(type)
    for (child in activeChildren.values) {
        child.broadcastEvent(type)
    }
}
```

先调用 `triggerEvent(type)`，使父级转移完整执行；随后直接遍历处理后的 `activeChildren`，因此旧子图不会收到，而新激活的子图会收到同一个事件。

这里不排序、不复制集合。子控制器广播只会修改自己的 `activeChildren`，不会修改父级的私有 Map，因此普通 DFS 遍历不会产生并发修改。`children` 与 `activeChildren` 均为私有字段，`StateAction` 只接收当前控制器，框架内的子控制器没有访问或修改祖先树的路径。

若业务层刻意保留根控制器的外部引用，并在子图 action 或覆写钩子中同步调用根控制器的 `reset()` / `broadcastEvent()`，则属于框架树之外的重入调用，不纳入第一阶段契约；接入方应将后续事件延迟到当前派发完成后。后续若需要支持这种集成模式，再在根入口增加事件队列或重入保护，而不是为正常热路径引入集合复制。

不将递归逻辑合并进 `triggerEvent()`，原因如下：

- `progress()` 依赖 `triggerEvent(null)` 只推进当前层；若它自动递归，会造成子控制器每帧被推进两次。
- 现有调用方可能依赖 `triggerEvent()` 的单层语义。
- 单层触发仍适合框架内部和精确控制场景，广播是更高一级的入口。

### 4.1 先修正控制器生命周期

广播依赖父级转移能够完整停用旧子图并启动新子图。当前实现存在以下确定性问题：

- `createStdLibStateMachine()` 默认 `start = true`，导致所有预构建子控制器在尚未激活时就执行初始节点 `onEntry`。
- 父级进入节点时再次调用 `child.reset()`，同一子图的初始 `onEntry` 会重复执行。
- 当前 `reset()` 先重置活跃子图，再重启父级；父级初始节点进入时又会重置同一批子图。
- KStateMachine 的 `stop()`/`restartBlocking()` 使用 `recursiveStop()`，不会执行状态 `onExit` 回调。
- 父级当前直接调用 `child.onExit(child.currentNode)`，只调用控制器钩子，遗漏 `StateNode.onExit` actions。

改造原则：所有控制器以 stopped 状态完成构造，整棵树构造完成后只显式启动根；子控制器只由父级当前节点激活。

建议将 KStateMachine 改为延迟启动：

```kotlin
private val stateMachine = createStdLibStateMachine(start = false) {
    // 节点 onEntry/onExit 统一委托给 enterNode/exitNode
}
```

统一节点生命周期：

```kotlin
private fun enterNode(node: StateNode) {
    currentNode = node
    node.onEntry.forEach { it.execute(this) }

    for (name in node.subGraphs.keys) {
        val child = children[name] ?: continue
        check(!child.isStarted) { "子控制器 $name 已被激活" }
        child.start()
        activeChildren[name] = child
    }

    onEntry(node) // 子类钩子，不再负责激活 children
}

private fun exitNode(node: StateNode) {
    for (child in activeChildren.values) {
        child.stop()
    }
    activeChildren.clear()

    node.onExit.forEach { it.execute(this) }
    onExit(node) // 子类钩子，不再负责递归退出 children
}
```

退出顺序固定为**内到外**：先完整停止活跃子图，再执行父节点的 `StateNode.onExit` actions，最后调用父控制器的 `onExit` 钩子。当前状态图的退出动作不得依赖子图仍处于活跃状态；现有动画与逻辑动作满足该前提。该顺序是本次生命周期改造的显式契约，后续新增退出 action 时也必须遵守。

公开生命周期入口：

```kotlin
val isStarted: Boolean
    get() = stateMachine.isRunning

fun start() {
    check(!stateMachine.isRunning) { "StateGraphController 已启动" }
    stateMachine.startBlocking()
}

fun stop() {
    if (!stateMachine.isRunning) return
    exitNode(currentNode)       // stopBlocking 本身不会执行状态 onExit
    stateMachine.stopBlocking()
}

open fun reset() {
    if (stateMachine.isRunning) stop()

    // 只清理由本控制器自己创建的容器，并在初始节点 entry 之前清理。
    if (ownsTags) tags.clear()
    if (ownsVariables) variables.clear()

    start()
}
```

共享容器的所有权语义保持不变：外部传入共享容器时，树中所有控制器都不拥有它，`reset()` 不负责清理；容器由创建它的工厂或上层 Holder 管理。只有控制器在未传入容器时自行创建的 storage 才由该控制器在 reset 中清理。

动画构建器应先递归构造 stopped 状态的完整树，再只对返回的每一个根控制器调用 `start()`。ARMS-Core 的 `MechaLogicStateMachine` 同样在 `super(...)` 完成、子类构造体就绪后启动根；其 gait/vertical children 保持 stopped，直到 posture 激活。

#### 延迟启动兼容性与迁移

这是 `StateGraphController` 生命周期的破坏性变更：构造器不再自动进入初始节点。已启动控制器上的 `triggerEvent()` 与 `progress()` 行为保持不变；对 stopped 控制器调用它们属于错误，应抛出明确的状态异常。

实施时必须：

- 审计 Spark-Core 内所有直接构造 `StateGraphController`、`AnimStateMachine` 与 `MultiAnimStateMachine` 的位置，并在树构造完成后启动每一个根控制器。
- 更新公开 API 文档和 ARMS-Core 接入说明：直接构造的根控制器必须由 Holder/工厂显式调用 `start()` 后才能 `progress()` 或接收事件。
- 保持子控制器只由父节点 `enterNode()` 启动，禁止业务层直接启动已挂载的子控制器。
- 为 stopped 控制器的 `start()`、`stop()`、`reset()`、`triggerEvent()` 与 `progress()` 分别添加契约测试；其中后两者必须验证未启动时失败而不会半执行 action。

`buildRootMachines()` / `buildRootMultiMachines()` 返回的是根控制器 Map，工厂必须启动其中的**每一个**根，而非只启动其中一个。

### 4.2 修正 ActionEvent.targetNode

找到转移时应记录目标 `StateNode`，而不是当前源节点。应在 KStateMachine 已确认转移的 `onTriggered` 回调中回写：

```kotlin
onTriggered {
    val target = stateToNodes[it.direction.targetState]
    it.event.targetNode = target
    onTriggered(it.event, stateToNodes[it.transition.sourceState], target)
}
```

这样 `targetNode` 只会在实际触发转移后赋值。无论采用何种等价实现，都应保证：

- 未发生转移时为 `null`。
- 发生转移时等于转移后的目标节点。
- 自转移时可以与源节点相同。

第一阶段无需新增复杂的 `EventDispatchResult`。广播调用方通过状态变化、`onTriggered` 钩子或调试日志观察各层转移即可。

---

## 5. 测试计划

优先使用不依赖 Minecraft 世界和动画资源的纯状态图测试。

### 5.1 基本行为

- [ ] 根控制器匹配事件时发生一次转移。
- [ ] 活跃直接子控制器匹配事件时发生一次转移。
- [ ] 活跃孙控制器匹配事件时发生一次转移。
- [ ] 未激活子控制器不会收到事件，状态保持不变。
- [ ] 子控制器构造完成但未激活时，不执行初始节点 `onEntry`。
- [ ] 未知事件不改变任何控制器状态。

### 5.2 并行广播

- [ ] 两个活跃并行子控制器均声明同名事件时，两者各转移一次。
- [ ] 子控制器与父控制器均声明同名事件时，父级先转移，子控制器后转移。
- [ ] 交换同层 children 的构造/插入顺序后，最终状态和共享变量结果保持一致。
- [ ] 对依赖兄弟执行顺序或由兄弟写入同一键的状态图进行设计期约束，不把 Map 遍历顺序当作功能契约。
- [ ] 同一事件在任一控制器内只执行第一条条件成立的转移，保持当前 `firstOrNull` 语义。

### 5.3 生命周期边界

- [ ] 父级事件导致退出当前状态时，旧子图不接收该事件。
- [ ] 父级事件激活的新子图会接收正在处理的同一个事件，并可据此转移。
- [ ] 父级条件在子图处理前求值；兄弟子图即使能观察到较早遍历者对共享 storage 的修改，最终结果也不得依赖这一顺序。
- [ ] 广播完成后再次 `progress()`，每个控制器只进行一次正常自动推进。
- [ ] 首次激活子图时，初始节点 `onEntry` action 与控制器钩子均恰好执行一次。
- [ ] 父级退出时，子图当前 `StateNode.onExit` action 与控制器钩子均恰好执行一次。
- [ ] reset 时旧活跃树 exit 一次、初始活跃树 entry 一次，不重复初始化 children。
- [ ] 未激活子图不执行 entry/exit。
- [ ] 自有 storage 在初始节点 entry 前清理；共享 storage 在 reset 前后保持由外部管理。
- [ ] exit 顺序固定为：活跃子图 exit、父节点 `StateNode.onExit` actions、父控制器 `onExit` 钩子。
- [ ] stopped 根控制器只有在 `start()` 后才能 `progress()` 或接收事件；失败路径不执行任何 action。

### 5.4 ActionEvent

- [ ] 无匹配转移时 `targetNode == null`。
- [ ] 有匹配转移时 `targetNode` 是目标节点而非源节点。

---

## 6. 兼容性与风险

| 项目 | 结论 |
|---|---|
| 现有 `triggerEvent()` | 已启动控制器上行为不变；stopped 控制器必须先 `start()` |
| `progress()` | 已启动控制器上行为不变，不改用广播；stopped 控制器必须先 `start()` |
| Bedrock 自动转移 | 使用 null event，不受影响 |
| `AnimStateMachine` | 继承新 API，无需改造 |
| `MultiAnimStateMachine` | 继承新 API，无需改造 |
| Java 调用 | `broadcastEvent(String)` 可直接调用 |
| 性能 | O(活跃控制器数)，广播路径不排序、不复制集合 |

主要风险是同名事件在新激活子图中也触发转移。该行为是父级优先广播的明确契约，应通过领域化事件命名和测试管理，而不是用隐式消费顺序隐藏。

---

## 7. 实施顺序

1. 配置 `src/test/kotlin` 的测试依赖和 JUnit Platform。
2. 将 StateGraphController 改为延迟启动，并实现统一的 `start()`/`stop()`/`reset()` 生命周期。
3. 审计所有直接构造点，调整动画控制器工厂和 ARMS-Core 逻辑控制器；树构造完成后启动返回 Map 中的每一个根，并更新公开迁移说明。
4. 添加 entry/exit/reset、stopped 控制器契约和共享 storage 所有权测试。
5. 修正 `ActionEvent.targetNode`，在已确认的 `onTriggered` 回调中写入目标节点。
6. 在 `StateGraphController` 增加无额外集合分配、父级优先的 `broadcastEvent(String)`。
7. 添加纯状态图广播测试，覆盖活跃树、并行子图、父级切换、新旧子图和兄弟顺序无关性。
8. 更新 `state_machine_unification_plan.md` 的子控制器约束，补充生命周期、事件广播语义和新 API。
9. 在 ARMS-Core 的 `MechaLogicStateMachine` 接入 `broadcastEvent()`，删除对显式子图名称路由的依赖。

---

## 8. 完成标准

- 根控制器只需调用一次 `broadcastEvent(eventName)`，事件即可沿父级处理后的活跃树到达全部后代。
- 未激活子图永远不会被事件改变。
- 多个活跃子图可确定性地响应同一事件。
- 父级切换后，旧子图不会收到事件，新激活子图会收到同一个事件。
- `triggerEvent(null)` 与现有每帧推进次数保持不变。
- `ActionEvent.targetNode` 与字段名称语义一致。
- 子控制器在激活前不执行 entry，退出和 reset 不漏 action、不重复 entry。
- 广播热路径不因排序或集合快照产生临时 Map/List。
- 兄弟子图交换遍历顺序后结果不变。

---

## 9. 实施总结

本次改造已完成以下核心变更：

### 代码变更

| 文件 | 变更 |
|------|------|
| `StateGraphController.kt` | 延迟启动（`start = false`）、`enterNode/exitNode` 生命周期、`isStarted/start()/stop()/reset()` 统一 API、`broadcastEvent(String)` 广播、`ActionEvent.targetNode` 修正、stopped 控制器守卫 |
| `OAnimStateMachineSet.kt` | `buildRootMachines()` / `buildRootMultiMachines()` 返回前调用 `.also { it.start() }` |
| `OAnimStateMachine.kt` | `build()` 返回前调用 `.also { it.start() }` |
| `build.gradle.kts` | 启用 ModDevGradle JUnit 集成，添加 JUnit 5 依赖并配置 `useJUnitPlatform()` |
| `StateGraphControllerTest.kt` (新文件) | 25 个状态图 JUnit 测试，覆盖生命周期契约、广播语义、targetNode、共享 storage |

### 文档更新

| 文件 | 变更 |
|------|------|
| `state_machine_unification_plan.md` | 更新 2.1 节 API 说明、5.6 节子控制器约束（延迟启动、enterNode/exitNode、broadcastEvent）、renumber 子节 |
| `sub_state_machine_event_broadcast_plan.md` | 标记状态为已完成，新增实施总结 |

### ARMS-Core

| 文件 | 变更 |
|------|------|
| `MechaLogicStateMachine.java` | 更新 Javadoc 使用 `broadcastEvent` 替代 `triggerEvent`，补充 `reset()`/`start()` 生命周期要求 |
