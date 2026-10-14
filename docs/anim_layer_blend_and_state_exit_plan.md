# Spark-Core 动画层混合口径与状态机退出的实例回收 —— 修正计划

> **状态**：待实施，尚未改动代码。本文只定缺陷、修法与验收，不含代码。
> **创建**：2026-10-14
> **修订**：2026-10-14 —— B 的口径收口为「层内分母与层权重同源」（§2.2）；修正 §1.6 的波及面、§4 的回归清单并补 A-1 判据；§1.1 / §1.2 / §1.4 精确化。
> **触发来源**：ARMS-Core `docs/表现层动画接入实现计划.md`（该文的表现层要同时使用 `MultiAnimStateMachine` 与多个常驻动画层）。两处缺陷由那份计划的审阅发现，但都不是 ARMS-Core 特有的——它们影响本仓库自己的状态机路径，以及 `../Machine-Max` 与任何内容包的 `animation_controllers`。
> **范围**：四项。A、B 是缺陷修正；C、D 是下游需要的两个小设施。
> **本文自包含**：结论、判据与公式写在文内；引用源码给「路径#符号」活引用，读者可在目标文件里检索该符号核验。
> **引用写法**：首次提及某文件时给完整路径，同一文件随后可用 `文件名#符号` 或 `.../子路径/文件名#符号`。两种省略前缀的含义：`.../` 指 `src/main/kotlin/cn/solarmoon/spark_core/`，`../Machine-Max/.../` 指 `../Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/`。

---

## 0. 概要

| 项 | 内容 | 性质 | 影响面 |
|----|------|------|--------|
| **A** | 状态机退出节点时不回收动画实例，循环动画永久留在层里 | 缺陷（实现与自身 KDoc 不一致） | 全部 `AnimStateMachine` / `MultiAnimStateMachine` 使用者 |
| **B** | 层内位置/缩放的分母是**全部实例**的权重和（旋转用的是含该骨骼的实例）；而多实例层的**层权重**又被写死为 `1.0f` | 缺陷（层内分母与层权重不同源，两半必须同一个量） | 唯一调用方 `AnimController.blendBone`（经 `AnimLayer.getBoneWeight` 取层权重） |
| **C** | `AnimGroups` 补一段低位层号常量 | 设施 | 新增，无既有调用点 |
| **D** | `PhysicsLevel` 补一个可查询的就绪入口 | 设施 | 新增，无既有调用点 |

一句话说明两条缺陷的可见后果：**A 让同一个层里的实例越积越多，B 让只在其中一个实例里出现的骨骼被压向模型原点。** 两者叠加时，表现为「切换几次之后整具模型越缩越小、越靠越近原点」。

B 的适用档位要说清：现状的系数是 `层权重 × currentWeight / 层内全部实例权重和`，**只有「层内全部实例权重和 = 1」时才正确**（新旧实例权重互补的常规交叉淡化正好落在这一档）。和 > 1 时参与实例被压小，和 < 1 时被放大。任何改动都不得破坏「和 = 1」那一档（判据见 §2.2 的等价性检查与 §2.4 的 B5/B6）。

两条缺陷都**已在仓库内被实证**，不是理论推演：

- **A**：`../Machine-Max` 的 `FabricatorBlockEntity`（`../Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/block/fabricator/FabricatorBlockEntity.java#updateState`）在状态切换时只调 `enter()`，从不调 `exit()`。
- **B**：同一处的 `workAnim` / `idleAnim` 两个实例都没有赋值 `group`，因此都落在 `AnimInstance#group`（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimInstance.kt#group`）的默认值 `AnimGroups.POSTURE` 上，即**同一层**；两个动画的骨骼集合不同，B 直接生效。本仓库自己的 `PlayerBaseAnimStateMachine#playRelativeAnim`（`src/main/kotlin/cn/solarmoon/spark_core/state_machine/presets/PlayerBaseAnimStateMachine.kt#playRelativeAnim`）与 `EntityBaseUseAnimStateMachine#onStateEntry`（`src/main/kotlin/cn/solarmoon/spark_core/state_machine/presets/EntityBaseUseAnimStateMachine.kt#onStateEntry`）则是在手动补偿 A（见 §1.3）。两处预设都不继承 `StateGraphController`，本项修改覆盖不到它们，补偿必须保留（见 §1.6）。

---

## 1. 缺陷 A：退出节点不回收动画实例

### 1.1 现状

`MultiAnimStateMachine.onExit`（`src/main/kotlin/cn/solarmoon/spark_core/animation/state/MultiAnimStateMachine.kt#onExit`）与 `AnimStateMachine.onExit`（`src/main/kotlin/cn/solarmoon/spark_core/animation/state/AnimStateMachine.kt#onExit`）的实现都只有三件事：

```
对 activeAnimInstances 逐个 eventHandlers.remove(AnimEvent.Tick::class)
activeAnimInstances.clear()
super.onExit(node)
```

**两者都不调用 `AnimInstance.exit()`。**

`StateGraphController.exitNode`（`src/main/kotlin/cn/solarmoon/spark_core/state_machine/graph/StateGraphController.kt#exitNode`）在离开节点时也只做三件事：停活跃子控、执行 `node.onExit` 的动作列表、调用 `onExit(node)` 钩子。整条退出路径上没有任何实例回收。

而实例从层里被摘掉只有一条路径：`AnimLayer.physicsTick` 的 `removeIf { it.state == AnimState.IDLE }`（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimLayer.kt#physicsTick`）。实例进入 `IDLE` 只有两条路径（`AnimState` 取值为 `IDLE / ENTER / WORK / EXIT`）：

| 路径 | 条件 | 对非 `Loop.ONCE` 动画是否成立 |
|------|------|-------------------|
| `AnimInstance.physTick` 内自动 Finish | `origin.loop == Loop.ONCE` 且 `time > maxLength` | 否。`Loop.TRUE` 只做 `step()`；`Loop.HOLD_ON_LAST_FRAME` 是 `if (time < maxLength) step()`，时间停在末帧、永远不触发 `Finish` |
| 有人调 `AnimInstance.exit()` | —— | 只由调用方触发 |

`Loop.TRUE` 与 `Loop.HOLD_ON_LAST_FRAME` 的动画因此**既不会自己结束，也不会被退出节点回收**：它们永久留在 `AnimLayer.animations` 里，`state` 停在 `WORK`，`currentWeight` 停在 `weight`。（内容包里的「停在末帧」动画同理，BMPT 清单中的 `death` / `run_idle` 就是 HOLD_ON_LAST_FRAME。）

`PlayAnimAction`（`src/main/kotlin/cn/solarmoon/spark_core/state_machine/graph/actions/PlayAnimAction.kt#execute`）是唯一把实例放进层的动作，且每个目标各新建一个实例——所以每次进入一个带 `PlayAnimAction` 的节点都会新增实例，而没有任何一步会减少它们。

### 1.2 实现与自身 KDoc 不一致

`StopAnimAction` 的 KDoc（`src/main/kotlin/cn/solarmoon/spark_core/state_machine/graph/actions/StopAnimAction.kt#StopAnimAction`）写着：

> 实际停止由 `AnimStateMachine.onExit` 统一处理：先移除 Tick handler，再调用父类 onExit。

这句话描述的是**尚未实现**的行为（该文档也写明了本 Action 当前实现是「停止全部」）。`StopAnimAction.execute` 自身做的事是对的（`ctrl.activeAnimInstances.forEach { it.exit() }`），但它被限制在 `AnimStateMachine` 上：`as? AnimStateMachine` 判空失败后告警并 `return`，因此在 `MultiAnimStateMachine` 上不可用。同一限制也出现在 `MoLangAction`、`SoundAction`、`ParticleAction`（各自的 `execute` 都以 `as? AnimStateMachine` 起步）。

也就是说，**状态机提供的动作里没有任何一条**能让一个 `MultiAnimStateMachine` 停止它播过的循环动画。外部仍有办法（`AnimController.stopAnimation(group)` / `stopAllAnimation()`、或直接 `AnimInstance.exit()`），但那要求调用方自己持有 target 与 `animGroup`。

本项只补两个 `onExit` 的实例回收——**不修**这四个 Action 的 `as? AnimStateMachine` 类型限制：`StopAnimAction` 在 `MultiAnimStateMachine` 上仍然不可用（只是它缺失后不再导致实例泄漏），`MoLangAction` / `SoundAction` / `ParticleAction` 同理。

### 1.3 已存在的补偿写法

本仓库两个内置预设都手动绕开了本缺陷，这正是缺陷成立的旁证：

| 位置 | 写法 |
|------|------|
| `.../state_machine/presets/PlayerBaseAnimStateMachine.kt#playRelativeAnim` | `lastAnim?.let { if (it.origin.loop != Loop.ONCE) it.exit() }`，在 `enter()` 新实例之前 |
| `.../state_machine/presets/EntityBaseUseAnimStateMachine.kt#onStateEntry` | 进 `none` 态时 `lastAnim?.exit()`；进入其它态时在 `enter()` 前再调一次 |

两处都自己维护 `lastAnim` 引用，因为引擎不替它们记。**这两处都不继承 `StateGraphController`，本项对 `onExit` 的修改覆盖不到它们，补偿必须保留**（见 §1.6）。

### 1.4 后果（定量）

`AnimLayer.blendBone` 对骨骼位置的口径是 `Σ(pos_i · w_i) / Σ_{全部实例} w_i`（见 §2.1），而 `animations.forEach` 的第一行 `it.origin.getBoneAnimation(boneName) ?: return@forEach` 会把**不含该骨骼**的实例跳过。于是只在其中一部分实例里出现的骨骼，其位置被按「参与权重 / 全部权重」的比例缩小：

| 该层实例数（各权重 1） | 骨骼只在其中一个实例里 | 位置系数 |
|---|---|---|
| 1 | 是 | 1（正常） |
| 2 | 是 | 1/2 |
| 3 | 是 | 1/3 |
| n | 是 | 1/n |

缩放同理被等比拉向 1。因为 A 让实例只增不减，这个系数会随每一次节点切换持续变小。位置是相对骨骼父级的偏移，所以视觉上是**该骨骼带着它的整条子链向父级收拢**——模型逐渐塌缩。

需要注意：这个折半是**由 A 触发的**，但它的直接成因是 B。只修 A、不修 B 的话，任何一次交叉淡化（新旧实例同层共存的过渡期，长度由 `AnimInstance.kt#inTransitionTime` 与 `#outTransitionTime` 决定，默认 0.15 s）仍会把两个动画的独占骨骼压一次。两项应一起改。

另一处要分清：**fabricator 不会「越积越多」**。`AnimInstance.enter()` 在 `enter` / `work` 态没有声明 `Start` 转移（`AnimInstance.kt#lifecycleStateMachine`），重复调用被忽略；而它只有 `workAnim` / `idleAnim` 两个实例、从不新建。它的症状是**恒定的 1/2 压缩**（两个满权重实例同层），不是逐步塌缩。实例累积只发生在 `PlayAnimAction` 路径（每次进节点 `new AnimInstance`），也就是内容包 `animation_controllers` 与 ARMS-Core 的表现层。实机验证 fabricator 时，不要因为「模型没有越缩越小」就判定 A 不存在。

### 1.5 修法

在两个 `onExit` 里补上实例回收，位置放在清理 Tick handler 之后、`activeAnimInstances.clear()` 之前：

```
override fun onExit(node: StateNode) {
    for (inst in activeAnimInstances) inst.eventHandlers.remove(AnimEvent.Tick::class)
    for (inst in activeAnimInstances) if (inst.state != AnimState.IDLE) inst.exit()
    activeAnimInstances.clear()
    super.onExit(node)
}
```

三点论证：

1. **时序正确，且自然构成交叉淡化。** kstatemachine 在状态切换时先触发源状态的 `onExit`、再触发目标状态的 `onEntry`；`StateGraphController` 把两者分别接成 `exitNode` / `enterNode`。`AnimInstance.exit()` 与 `AnimInstance.enter()` 都经 `holder.animLevel!!.physicsLevel.submitImmediateTask` 投到物理线程、进同一条 FIFO（`TaskSubmitOffice.kt#submitImmediateTask` 是 `ConcurrentLinkedDeque.add`），而 `PhysicsLevel.prePhysicsTick` 在广播 `PhysicsLevelTickEvent.Pre` **之后**调用 `processTasks(PPhase.ALL)`。所以「旧实例进 EXIT」先于「新实例进 ENTER」被处理，出场过渡照常走完。

2. **重复 `exit()` 安全。** 已 IDLE 的实例收到 Stop 事件时，其生命周期状态机在 `idle` 态没有声明 Stop 转移（`AnimInstance.kt#lifecycleStateMachine`），事件被忽略。这条不是推断：`EntityBaseUseAnimStateMachine#onStateEntry` 已经在 `lastAnim` 可能已 IDLE（上一个动画是 `Loop.ONCE` 且已播完，此时 `isFinished` 为真）的情况下无条件调用 `lastAnim?.exit()`，属既有已发布路径。**但 `state != AnimState.IDLE` 判据只省一次投递，不承担正确性**：同一 tick 内，节点的 `onExit` 动作 `StopAnimAction` 与本钩子会给同一个实例投两次 Stop（状态在物理线程更新，主线程读到的仍非 IDLE），第二次在 `exit` 态被忽略——安全性完全来自上一条。

3. **`animLevel` 可空性：采纳「容忍空值」并退化为在调用线程直接处理事件。** 现在 `AnimInstance.enter()` / `exit()` 是 `holder.animLevel!!.physicsLevel.submitImmediateTask { ... }`；改为 `holder.animLevel?.physicsLevel?.submitImmediateTask { ... } ?: lifecycleStateMachine.processEventBlocking(...)`，与 `IAnimatable.putVariable`（`src/main/kotlin/cn/solarmoon/spark_core/animation/IAnimatable.kt#putVariable`）的既有写法同构。三点理由：① 空 `animLevel` 意味着没有物理线程会驱动这个实例，在调用线程直接处理不引入并发；② 若选择「跳过回收」，A-1 将不可判定（修复前后都只是 `activeAnimInstances.clear()`，见 §4）；③ 顺带消掉裸 `!!`。代价：持有者 `animLevel` 为空时，`PlayAnimAction` 从「NPE 崩溃」变成「进入状态但权重不推进」——这是本项接受的取舍。若不愿改生产语义，A-1 就必须搭假的 `Level` + `PhysicsLevel` 替身（成本更高，见 §4）。

### 1.6 波及面

`AnimStateMachine` / `MultiAnimStateMachine` 的构造点全在本仓库内部：

| 使用方 | 位置 | 受本项影响 |
|--------|------|-----------|
| 内容包 JSON 状态机（广播模式） | `.../animation/state/origin/OAnimStateMachineSet.kt#buildRootMultiMachines` | 获得 KDoc 承诺的行为：退出节点的循环动画被回收 |
| 内容包 JSON 状态机（1:1 模式） | `.../animation/state/origin/OAnimStateMachineSet.kt#buildRootMachines` | 同上 |
| 单控制器构建入口 | `.../animation/state/origin/OAnimStateMachine.kt#build` | 同上 |
| `PlayerBaseAnimStateMachine` | `.../state_machine/presets/PlayerBaseAnimStateMachine.kt#playRelativeAnim` | **不受本项影响**：它是 `StateMachineHandler` + 自建 `createStdLibStateMachine`，不是 `StateGraphController` 子类，`onExit` 钩子不会被调用；手动 `exit()` 必须保留 |
| `EntityBaseUseAnimStateMachine` | `.../state_machine/presets/EntityBaseUseAnimStateMachine.kt#onStateEntry` | 同上 |
| 直接持有 `AnimInstance` 的调用方 | `../Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderable/GuiAnimatable.java#GuiAnimatable`、`../Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/block/fabricator/FabricatorBlockEntity.java#updateState` | **不受本项影响**：它们不走状态机，实例生命周期自管；但受 §2 影响（见 §0） |
| ARMS-Core 表现层（计划中） | ARMS-Core `docs/表现层动画接入实现计划.md` | 依赖本项落地 |

`../BallisticsFramework` 全文不含 `AnimInstance` / `AnimStateMachine` / `AnimLayer` / `blendBone` 的任何引用。

---

## 2. 缺陷 B：层内分母与层权重不同源

### 2.1 现状

`AnimLayer.blendBone`（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimLayer.kt#blendBone`）的核心：

```kotlin
val totalWeight = animations.sumOf { it.currentWeight.toDouble() }
if (totalWeight <= 0.0) return KeyAnimData()
val pos = tmpPos.zero()
val rot = boneBaseRot; var accumulatedWeight = 0f
val scale = tmpScale.set(1f, 1f, 1f)
animations.forEach {
    val boneData = it.origin.getBoneAnimation(boneName) ?: return@forEach
    val pt = (it.currentWeight / totalWeight).toFloat()
    pos.add(boneData.getAnimPosAt(time, it).mul(pt))
    scale.add(boneData.getAnimScaleAt(time, it).mul(pt)).sub(tmpScaleSub.set(pt, pt, pt))
    val origRot = boneData.getAnimRotAt(time, it).toQuaternionf()
    if (accumulatedWeight == 0f) { rot.set(origRot); accumulatedWeight = pt }
    else { val t = pt / (accumulatedWeight + pt); rot.slerp(origRot, t); accumulatedWeight += pt }
}
```

`totalWeight` 的分母是**本层全部实例**的权重和，与 `boneName` 无关；而循环体第一行就把不含该骨骼的实例跳过了。三条通道因此口径不一：

| 通道 | 实际公式 | 分母 | 是否正确 |
|------|----------|------|----------|
| 旋转 | `slerp` 的插值系数 `pt / (accumulatedWeight + pt)` | `accumulatedWeight`，只累加**含该骨骼**的实例 | **正确**：`1/totalWeight` 被约掉，等价于按原始权重在参与实例之间归一 |
| 位置 | `Σ (animPos_i · pt_i)` | `totalWeight`，含**全部**实例 | **错误**：参与实例的位置被等比缩小 |
| 缩放 | `1 + Σ ((animScale_i − 1) · pt_i)` | 同上 | **错误**：同上 |

**单实例层不受影响**：分母就是那个实例自己的 `currentWeight`，层内比例恒为 1，层权重也正好等于它。多实例层则要按**层内全部实例权重和（记为 `wAll`）**分档——只有 `wAll = 1` 时现状才是对的，其余两档都错：`wAll > 1` 被压小（调用方把多个满权重实例放进同一个 `group`，例如 `../Machine-Max` 的 fabricator 与 `Part.playAnim`，见 §0 与 §2.3），`wAll < 1` 被放大（多个实例同时处在半途）。

对照 `AnimController.blendBone`（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimController.kt#blendBone`）：它对每个层取 `weight = layer.getBoneWeight(boneName)`，再用这个值把下层已经算出的结果 `lerp` / `slerp` 向本层结果拉（`OVERRIDE`）。所以

```
一个实例对某根骨骼的实际贡献 = 层权重 × 层内分母给出的比例
```

两处必须同源。而 `AnimLayer.kt#getBoneWeight` 在层内有多个实例时**直接返回 `1.0f`**（只有 `animations.size == 1` 时才返回那一个实例的 `currentWeight`）。记「含该骨骼的参与实例权重和」为 `wSum`、「层内全部实例权重和」为 `wAll`，按档列出现状的实际系数：

| 档 | 判据 | 现状实际系数 | 现状行为 |
|----|------|--------------|----------|
| 单实例层 | `animations.size == 1` | `currentWeight × 1` | 淡化由层权重承担，正确 |
| 新旧互补的交叉淡化 | `wAll = 1` | `1.0 × currentWeight / 1` = `currentWeight` | **正确**——这就是今天切换看起来没问题的原因 |
| 多实例满权重（fabricator、`Part` 多动画） | `wAll > 1` 且 `wAll > wSum` | `1.0 × currentWeight / wAll` | **错误**：被等比压小 |
| 多个实例同时处在半途（都未满权重） | `wAll < 1` | 同上 | **错误**：被等比放大 |

由此得到本项修法的两条约束：**层内分母改成 `wSum`（只含该骨骼的实例）；层权重也必须改成同一个 `wSum`（封顶 1）。** 只改前者的后果见 §2.2 与 §2.4 的 B5/B6。

### 2.2 修法

对**参与该骨骼的实例**归一，并让层权重用**同一个和**。单趟完成，不必两遍扫描：

```
wSum     = Σ w_i                     // 只累加含该骨骼的实例，w_i = currentWeight_i
posSum   = Σ (animPos_i · w_i)
scaleDlt = Σ ((animScale_i − 1) · w_i)
最终：pos = posSum / wSum            // scale = 1 + scaleDlt / wSum
层权重  = min(1, wSum)               // AnimLayer.getBoneWeight 的多实例分支
```

五点说明：

1. **层权重必须用同一个和。** 层内归一化对参与权重是 0 次齐次——把参与实例的 `currentWeight` 同乘 k，层内结果不变。进度就在 `currentWeight` 里，一旦被层内归一「约掉」，就只能靠层权重把它放回来。单实例层一直如此（层权重就是那个实例的 `currentWeight`）。
2. **不能把进度乘在层内。** 两种写法都不行：① 乘进分子又乘进分母，仍被约掉；② 乘在层内算出的姿势上，那是「把本层姿势整体缩小」，而 `OVERRIDE` 的语义是「从下层结果按比例滑向本层结果」——下层仍在驱动同一根骨骼时两者不等价（下层 10、本层 4、比例 0.5：正确值是 7，缩放写法得到 2）。淡入淡出属于「这一层有多算数」，不属于「这一层想要什么姿势」。
3. **聚合方式取「求和封顶」。** 多个参与者同时在场时，层权重 = 参与 `currentWeight` 之和、封顶 1。理由：与单实例层同构；新旧互补时和正好是 1，交叉淡化全程 100%（不会中途瘪下去）；只有和 > 1 时才产生差异，而那正是要修的档。已知取舍：一个状态里放 N 个动画、作者权重都是 1 时，层权重会以 N 倍速度到达 100%——约定为「一个状态放多个动画时，作者权重之和宜为 1」（例如各 1/N），而不是改聚合方式。
4. 旋转沿用现有 `accumulatedWeight` 逻辑不动（它已经等价于按参与实例的原始权重归一，改它只会引入回归）。
5. `wSum <= 0` 时返回 `KeyAnimData()`。采用同源口径后该分支从 `AnimController` 不可达（层权重同时为 0，层会被跳过），只作兜底。

**等价性检查（防回归的关键）**：实际系数 = `min(1, wSum) / wSum × w_i`，于是

- 单实例层：`wSum = w` → 系数 = `w`，且层内 `pt = 1` → **与现状逐位一致**；
- 新旧互补的交叉淡化（`wAll = 1`，此时 `wSum <= 1`）：系数 = `w_i` → **与现状逐位一致**（B5/B6 就是这一档）；
- `wSum > 1`：系数 = `w_i / wSum` → 层内变成加权平均，压缩消失（B1/B4）。

也就是说：**现状本来就对的两档（单实例层、`wAll = 1` 的互补交叉淡化）与「所有实例都含该骨骼且权重和 ≥ 1」（B2/B3）零行为变化**；其余情况都是现状本来就错、由本项修正——包括「所有实例都含该骨骼但权重和 < 1」（多个实例同时淡出，现状会把姿势冻结到被摘除那一帧）。

### 2.3 波及面

`AnimLayer.blendBone` 只有一个调用方：`AnimController.blendBone`。本仓库、`../Machine-Max`、`../BallisticsFramework` 三处都没有第二个直接使用者。`AnimLayer` 的构造点也只有 `AnimController.kt#playAnimation` 的 `layers.getOrPut(anim.group) { AnimLayer() }`。

但 `AnimLayer` 的**其他成员**确有下游读者：`../Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/Part.java#playAnim` 直接读 `animController.getLayers()[AnimGroups.ACTION].getAnimations()`，按动画名精确 `exit()` 旧实例后再 `enter()` 新实例。它的注释写明「同一零件上的多个事件动画（如一门机炮 + 一挺机枪共用同一 Part）只要动画名不同即可同时播放」——这是**设计内的同层多实例**，也正是 B1 的现实对应物（两根满权重实例同层，某根骨骼只属于其中一个）。本项不改变 `animations` 的读写约定，`Part.playAnim` 不需要动。

### 2.4 验收

数值用例（纯逻辑，不需要资源包）：

| 用例 | 层内实例 | 期望 | 现状 |
|------|----------|------|------|
| B1 | A 含骨骼 X，`position = (4, 0, 0)`，权重 1；B 不含 X，权重 1 | `blendBone("X").position == (4, 0, 0)` | `(2, 0, 0)` |
| B2 | A、B 都含 X，`position` 分别为 `(4,0,0)`、`(0,2,0)`，权重 1:1 | `(2, 1, 0)` | `(2, 1, 0)`（此例两分母相等，现状恰好也对） |
| B3 | A 权重 1 含 X = `(4,0,0)`；B 权重 3 含 X = `(0,4,0)` | `(1, 3, 0)` | `(1, 3, 0)` |
| B4 | 缩放：A 含 X 且 `scale = 2`，权重 1；B 不含 X，权重 1 | `2` | `1.5` |
| B5 | A 含 X = `(4,0,0)`，`currentWeight = 0.25`（EXIT 中）；B 不含 X，`currentWeight = 0.75`（ENTER 中） | `(1, 0, 0)` | `(1, 0, 0)` |
| B6 | 缩放：A 含 X 且 `scale = 3`，`currentWeight = 0.25`；B 不含 X，`currentWeight = 0.75` | `1.5` | `1.5` |
| B7 | `AnimLayer.getBoneWeight`：A、B 都含 X，`currentWeight` 各 0.5（两根同时入场到一半） | `1.0`（求和封顶） | `1.0`（多实例恒 `1.0f`） |

覆盖关系：**B1/B4 是 `wSum > 1` 档（压缩）的修复判据；B5/B6 是 `wSum < 1` 档（交叉淡化）的回归判据**——只改层内分母会把它们从 `(1,0,0)` / `1.5` 变成 `(4,0,0)` / `3`（满值 + 移除瞬间跳变），这是本项最容易回归的地方；B2/B3 是「多实例且都含该骨骼」的保护判据（两分母被约掉，行为本就不变）；B7 不区分现状与修法，作用是**锁定聚合方式**（对照：若取「最大值」，这一例会得到 `0.5`）。

---

## 3. 顺带项

### 3.1 C：`AnimGroups` 的低位层号段

`AnimGroups`（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimGroups.kt#AnimGroups`）现有取值是 2 的幂：`AMBIENT = 1`、`LOCOMOTION = 2`、`POSTURE = 4`、`ACTION = 8`、`OVERRIDE = 16`。低于 `AMBIENT` 的可用整数只有 0 与负数。

下游（ARMS-Core）需要「若干常驻层各占一个层号，且整体低于全部语义层」的一段，现在只能在使用者侧自取负数——`AnimController.layers` 是 `sortedMapOf<Int, AnimLayer>`（TreeMap），任何 `Int` 都合法（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimController.kt#layers`），所以功能上可行，但「低位段属于谁」变成了使用者的私有约定。

建议在本对象里给出**确定值**与取槽入口，把「低于全部语义层的常驻段」从使用者的私有约定升级为引擎词汇表的一部分（下游要把槽位写进图与文档，数值不该留到实施时再定）：

```
const val PERSISTENT_BASE  = -8      // 常驻段最低槽，整体低于 AMBIENT = 1
const val PERSISTENT_SLOTS = 8       // 槽位号 0..7 → 层号 -8..-1
fun persistent(slot: Int): Int { require(slot in 0 until PERSISTENT_SLOTS); return PERSISTENT_BASE + slot }
```

两点配套：① 顶部 KDoc 现在写「按 bit 位区分」，加入连续低位段后要改成「语义层为 2 的幂 + 常驻段为低位连续整数」；② 层号 0 与正数区间保持空闲，留给以后可能新增的语义层。`AnimGroups` 的常量全仓库只当层键使用、没有位运算用法，所以新增负数常量不会影响任何既有代码。**无既有调用点**，纯新增，零风险。

### 3.2 D：`PhysicsLevel` 的就绪查询

`LevelMixin.getPhysicsLevel`（`src/main/java/cn/solarmoon/spark_core/mixin/extension/LevelMixin.java#getPhysicsLevel`）在内部字段为空时抛 `IllegalStateException`，且返回类型不可空。调用方要「取值前先判」只能靠捕获异常——这是用异常做控制流。

建议在 `LevelPatch` 上补一个可空入口（`getPhysicsLevelOrNull()`，Kotlin 侧即属性 `physicsLevelOrNull`）——比 `isPhysicsReady(): Boolean` 更有用（后者可由前者推出）。**属纯追加**：现有调用点（本仓库与 `../Machine-Max` 共数十处）不需要改。三点实施细节：

1. **实现必须落在 `LevelMixin`**（`src/main/java/cn/solarmoon/spark_core/mixin/extension/LevelMixin.java#physicsLevel` 是它的 private 字段，Java `default` 方法读不到），`LevelPatch` 上只加抽象方法；全仓库只有 `LevelMixin` 一个实现者。
2. **Kotlin 侧补对称入口**：`SparkLevel.getPhysicsLevelOrNull(level)` + `val Level.physicsLevelOrNull: PhysicsLevel?`（`src/main/kotlin/cn/solarmoon/spark_core/api/SparkLevelKt.kt#physicsLevel` 已是这个形状），否则 Kotlin 下游只能用不安全的 cast 去碰 `LevelPatch`。
3. **保留抛异常的旧入口**：多数既有调用点「未就绪」确实应该是异常；新入口只服务客户端渲染 / tick 一类需要守卫的调用方。另注意它与 `SparkLevel.isSparkLevel`（`src/main/java/cn/solarmoon/spark_core/api/SparkLevel.java#isSparkLevel`）不是同一个问题——后者问的是「有没有被 Mixin」，与物理级别是否已建立无关。

---

## 4. 验证与回归

| 项 | 车道 | 判据 |
|----|------|------|
| A | JUnit | A-1（见下） |
| B | JUnit | §2.4 的 B1–B7 |
| C / D | 编译期 | 新常量与新入口可被引用；既有调用点零改动（`./gradlew --offline build` 通过即可） |

**测试缝（实施 A、B 前要先定下来）。** 现在 `src/test/` 下没有任何 `IAnimatable` 替身，且 `AnimInstance.currentWeight` 是 `private set`、只有 `stepWeight()`（由 `physTick` 在 `ENTER` / `EXIT` 态驱动）会写它。仓库已有 900 行的 `src/test/kotlin/cn/solarmoon/spark_core/state_machine/graph/StateGraphControllerTest.kt`（`twoNodeGraph` / `LifecycleRecorder` 等脚手架，`useJUnitPlatform()` 已配好），两个用例都应以它为起点。

- **B 的用例**需要一个测试可见的权重入口。最小改动是把 `currentWeight` 的 setter 放宽到 `internal`（或加一个 `internal` 的方法）；`AnimLayer.animations` 本身已是公开 `LinkedHashSet`，`AnimInstance(holder, origin)` 的主构造器是 `internal`（同模块测试可见），只要再有一个能构造出 `OAnimation` 的途径（`OAnimation.CODEC` 直接解码一段 JSON 即可，不需要内容包）就能把实例塞进层里。替身只需 `animLevel == null` 即可：`AnimLayer.blendBone` 只读 `currentWeight` 与 `typedTime`，关键帧是纯数字常量时走 `MolangConstantCache` 不碰 holder，`ModelController(this)` 的构造也只做 `OModel.getOrEmpty`（未知索引返回空集），约十几行。
- **A 的用例 A-1**：① 取 §1.5 第 3 点的退化路径（`animLevel == null` 的替身 + `AnimStateMachine` / `MultiAnimStateMachine` 的测试子类）；② 构造一个 `AnimInstance` 并 `enter()`，断言其 `state` 已不是 `IDLE`，再把它放进 `activeAnimInstances`；③ 触发一次节点退出（`triggerEvent` 造成转移，或走 `stop()` / `exitNode` 路径）；④ 断言退出后该实例 `state == AnimState.EXIT`（进而到 `IDLE`）。**红→绿判据是第 ④ 步的实例状态**：实施前实例会停在 `ENTER` / `WORK`；而「`activeAnimInstances` 为空」这一条在修复前后都成立，不能单独作为判据。

**回归清单**：`./gradlew --offline test`；`./gradlew --offline runClient`：

1. **B 的首选靶子**：`PlayerBaseAnimStateMachine` 的行走 / 游泳 / 蹲伏切换——它只受 B 影响（新旧实例同层共存于 `LOCOMOTION` / `POSTURE`），盯切换全过程是否稳定、中途是否「瘪」、末端是否跳变；
2. **B 的第二靶子**：`../Machine-Max` 的 `Part.playAnim`（设计内的同层多实例）与 fabricator 的 `workAnim` / `idleAnim`（两个满权重实例）——后者的独有骨骼修好后应从 1/2 恢复为满位移；它**不会**「越缩越小」，不要以此判定 A；
3. **A 的实机靶子（可选）**：给某个模型加一个两状态 `animation_controllers`，两态各播一个 `loop: true` 动画来回切换，观察 `AnimLayer.animations.size` 是否收敛。仓库自带的 `base_controller.json` 两个状态 `animations` 都是空数组，不触发 `PlayAnimAction`，**不能**当靶子。

---

## 5. 不变部分

- `StateGraphController` 的节点生命周期钩子签名与调用顺序不变（`src/main/kotlin/cn/solarmoon/spark_core/state_machine/graph/StateGraphController.kt#enterNode` 与 `#exitNode`）。
- `AnimGroups` 的层优先级语义（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimGroups.kt#AnimGroups`）与 `BlendMode` 的两种模式语义（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/BlendMode.kt#BlendMode`）不变。
- `PlayAnimAction` 的分路逻辑与三级回退（目标自身 → 素体 → 内置）不变（`src/main/kotlin/cn/solarmoon/spark_core/state_machine/graph/actions/PlayAnimAction.kt#execute`）。
- `AnimLayer.getBoneWeight` 的骨骼名缓存（`cachedBoneNames`）与「不含该骨骼返回 `0.0f`」不变；**返回口径改变**：单实例层不变，多实例层由恒 `1.0f` 改为 `min(1, Σ参与 currentWeight)`（§2.2，原「返回口径不变」的约定作废）。
- `AnimInstance` 的生命周期状态机（`idle → enter → work → exit → idle`）与 `Loop` 三种模式的行为不变；唯一改动是 `enter()` / `exit()` 在 `animLevel == null` 时退化为在调用线程直接处理事件（§1.5 第 3 点），`animLevel` 非空时行为不变（`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimInstance.kt#lifecycleStateMachine`、`src/main/kotlin/cn/solarmoon/spark_core/animation/anim/origin/Loop.kt#Loop`）。
