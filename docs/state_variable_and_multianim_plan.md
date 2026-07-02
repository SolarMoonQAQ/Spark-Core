# Spark-Core 状态变量容器 & 多模型动画状态机

> **状态**: 草案 · **创建**: 2026-07-01 · **修订**: 2026-07-02 (审阅修正)\
> **关联**: ARMS-Core 分层控制器设计 · state_machine_unification_plan.md

***

## 目录

1. [背景与动机](#1-背景与动机)
2. [状态变量容器](#2-状态变量容器)
3. [MultiAnimStateMachine](#3-multanimstatemachine)
4. [PlayAnimAction 分路改造](#4-playanimaction-分路改造)
5. [MoLang 上下文与接口组合](#5-molang-上下文与接口组合)
6. [实施步骤](#6-实施步骤)
7. [不变部分](#7-不变部分)
8. [线程安全说明](#8-线程安全说明)

***

## 1. 背景与动机

### 1.1 问题

`StateGraphController` 仅持有 `GameplayTagContainer tags` 作为运行时数据源。纯布尔型无法承载速度、方向等数值。

ARMS-Core 的机娘由**多个独立渲染的 SubPart** 组成，需要：

| 需求 | 说明 |
|------|------|
| 数值型状态 | speed、verticalSpeed、inputForward 等 float/int 值 |
| 多模型广播 | 一个状态机向多个 IAnimatable 播放同名动画 |
| 默认动画回退 | 零件未定义某动画时，回退到素体/Mod内置动画 |
| 分层广播 | 不同控制器播不同 AnimGroup + 不同 target 子集 |
| 对齐 YSM MoLang | `ctrl.xxx` 命名空间，降低 UGC 迁移成本 |
| 接口组合绑定 | 按功能域拆分 `@QueryBinding` 到接口，支持多接口组合 |

### 1.2 目标

1. `StateVariableContainer` — 类型化、带默认值的状态变量容器
2. `MultiAnimStateMachine` — 继承 `StateGraphController`（非 `AnimStateMachine`），1:N 广播
3. `PlayAnimAction` 分路 — `when(controller)` 自动区分单播与广播
4. 接口式 MoLang 绑定 — `MechaCtrlBindings` 等接口通过 default 方法 + `@QueryBinding` 组合
5. 不破坏现有 `PlayAnimAction` / `AnimStateMachine`

---

## 2. 状态变量容器

参考 BallisticsFramework `BFDamageExtensions` 模式——每个 key 带默认值，`contains()` 区分"未写入"与"显式写入默认值"。附带 `type: KClass<T>` 字段供运行时类型检查，防止泛型擦除导致同 id 不同类型 key 的误用。

### 2.1 类定义

```kotlin
// graph/StateVariableKey.kt
data class StateVariableKey<T>(
    val id: ResourceLocation,
    val type: KClass<T>,      // 保留运行时类型，防止泛型擦除导致同 id 误用
    val defaultValue: T
)

// graph/StateVariableContainer.kt
class StateVariableContainer {
    private val data = mutableMapOf<StateVariableKey<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: StateVariableKey<T>): T =
        if (data.containsKey(key)) data[key] as T else key.defaultValue

    fun <T> set(key: StateVariableKey<T>, value: T) { data[key] = value }

    /** 区分"未写入"与"显式写入默认值" */
    fun contains(key: StateVariableKey<*>): Boolean = data.containsKey(key)

    /** 重置为默认值（从 map 中移除该条目） */
    fun <T> remove(key: StateVariableKey<T>) { data.remove(key) }

    /** 清空所有写入，全部回退到默认值 */
    fun clear() { data.clear() }

    /** 帧首快照覆盖 */
    fun replaceAll(source: StateVariableContainer) {
        data.clear(); data.putAll(source.data)
    }
}
```

### 2.2 挂载到 StateGraphController

```kotlin
open class StateGraphController(
    val stateMachineGraph: StateMachineGraph,
    private val children: Map<String, StateGraphController> = mapOf()
) {
    val tags = GameplayTagContainer()          // 保留，标记型
    val variables = StateVariableContainer()   // 新增，数值型
}
```

**注意**：`reset()` 方法中需要同步清理 `variables`（调用 `variables.clear()`），防止切换回初始状态后残留脏数据。

在 `MoLangCondition` 中，`AnimStateMachine` 沿用现有 `evalAsBoolean(animatable)` 路径，`MultiAnimStateMachine` 则通过 `contextProvider()` 构造参数直接获取 `MolangContext`，调用 `MolangContextRegistry.compile().evaluate()` 原始求解。无需在 `StateGraphController` 上新增模板方法。

### 2.3 预定义 Key

```kotlin
// presets/StateVariableKeys.kt
object StateVariableKeys {
    val ON_GROUND       = key("spark_core:on_ground", false)
    val SPEED           = key("spark_core:speed", 0f)
    val VERTICAL_SPEED  = key("spark_core:vertical_speed", 0f)
    val IS_SPRINTING    = key("spark_core:is_sprinting", false)
    val IS_SWIMMING     = key("spark_core:is_swimming", false)
    val IS_DEAD         = key("spark_core:is_dead", false)
    val INPUT_FORWARD   = key("spark_core:input_forward", 0f)
    val INPUT_STRAFE    = key("spark_core:input_strafe", 0f)
    val ALL_ANIMATIONS_FINISHED  = key("spark_core:all_animations_finished", true)
    val ANY_ANIMATION_FINISHED   = key("spark_core:any_animation_finished", true)

    private fun <T> key(name: String, default: T) =
        StateVariableKey(ResourceLocation.parse(name), default::class, default)
}
```

---

## 3. MultiAnimStateMachine

### 3.1 为什么不继承 AnimStateMachine

`AnimStateMachine` 需要完整的 `IAnimatable`——用于 MoLang 上下文、AnimController、动画完成写入。`MultiAnimStateMachine` **不需要** `AnimController` 和 `ModelController`，只需要：

1. MoLang 求值上下文（用于转移条件）
2. 一个 `IAnimatable` 列表作为广播目标
3. AnimGroup 指定

因此**直接继承 `StateGraphController`**，与 `AnimStateMachine` 并列。

### 3.2 内置默认动画

作为 Spark-Core 库模组，不同下游模组（ARMS-Core、Machine-Max 等）需要不同的内置默认动画。因此以 `OAnimationSet?` 作为**实例级构造参数**，由下游模组在构造时传入已加载的动画集实例。不再存 RL，避免 `getOrEmpty` 的类型不匹配（该方法接受 `ModelIndex?`，非 `ResourceLocation?`）。

### 3.3 类定义

```kotlin
package cn.solarmoon.spark_core.animation.state

/**
 * 多模型动画状态机。
 * 
 * 每个 target 通过三级回退查找同名动画：
 * 1. target 自己的 OAnimationSet
 * 2. fallbackAnimations (素体/MechaControl 提供)
 * 3. builtinAnimations (Mod 内置，由下游模组构造时传入已加载的 OAnimationSet 实例)
 * 
 * 动画完成状态仅检查本机播放的动画（[activeAnimInstances]），
 * 不遍历 target 的所有 layer，避免被其他控制器干扰。
 *
 * [contextProvider] 提供 MoLang 上下文（通常来自宿主实体），
 * 用于 [MoLangCondition] 求值转移条件。
 */
open class MultiAnimStateMachine(
    graph: StateMachineGraph,
    /** 广播目标列表 */
    val animTargets: List<IAnimatable<*>>,
    /** MoLang 上下文提供者（来自宿主实体，而非某个零件） */
    val contextProvider: () -> MolangContext<*>,
    /** 实例级默认动画集（素体提供，每个机甲不同），可为空 */
    var fallbackAnimations: OAnimationSet? = null,
    /** Mod 内置默认动画集（由下游模组构造时传入已加载实例），可为空 */
    var builtinAnimations: OAnimationSet? = null,
    /** 写入的目标层 */
    val animGroup: Int = AnimGroups.AMBIENT,
    children: Map<String, StateGraphController> = mapOf()
) : StateGraphController(graph, children) {

    /** 当前状态的活跃动画追踪列表（由 PlayAnimAction 维护，仅本机播放的动画） */
    val activeAnimInstances = mutableListOf<AnimInstance>()

    /**
     * 三级动画查找：
     * 1. 零件自己 → 2. 素体 → 3. Mod 内置动画集 → null
     */
    open fun findAnimation(target: IAnimatable<*>, animName: String): OAnimation? {
        target.animController.originAnimations[animName]?.let { return it }
        fallbackAnimations?.getAnimation(animName)?.let { return it }
        builtinAnimations?.getAnimation(animName)?.let { return it }
        return null
    }

    /**
     * 在每帧转移条件求值前调用，检查本机播放的动画完成状态。
     * 与 AnimStateMachine 一致：仅迭代 [activeAnimInstances]。
     */
    override fun onCheckTransition() {
        var allDone = true
        var anyDone = false
        for (inst in activeAnimInstances) {
            if (inst.isFinished) anyDone = true
            else allDone = false
        }
        if (activeAnimInstances.isEmpty()) { allDone = true; anyDone = true }
        variables.set(StateVariableKeys.ALL_ANIMATIONS_FINISHED, allDone)
        variables.set(StateVariableKeys.ANY_ANIMATION_FINISHED, anyDone)
    }

    override fun onExit(node: StateNode) {
        for (inst in activeAnimInstances) {
            inst.eventHandlers.remove(AnimEvent.Tick::class)
        }
        activeAnimInstances.clear()
        super.onExit(node)
    }
}
```

### 3.4 与 AnimStateMachine 的类层次

```
StateGraphController
  │   + variables: StateVariableContainer  ← 数值型状态
  │
  ├─ AnimStateMachine              ← 1:1 动画状态机 (本地用)
  │     animatable: IAnimatable     ← MoLangCondition 直接通过 controller.animatable 求值
  │
  └─ MultiAnimStateMachine         ← 1:N 动画状态机 (中央用)
        animTargets: List           ← 广播目标
        contextProvider             ← MoLangCondition 直接通过 controller.contextProvider() 求值
        fallbackAnimations          ← 素体默认动画
        builtinAnimations           ← Mod 内置动画集实例
        animGroup                   ← 层号
```

---

## 4. PlayAnimAction 分路改造

```kotlin
// animation/state/actions/PlayAnimAction.kt — execute() 改造

override fun execute(controller: StateGraphController) {
    when (controller) {
        is MultiAnimStateMachine -> {
            for (target in controller.animTargets) {
                val anim = controller.findAnimation(target, animName) ?: continue
                // buildInstance 与 animInstance 类似，但接受已解析的 OAnimation，
                // 避免重复查找。enter() 内部通过 KStateMachine 自动调用 playAnimation
                val instance = buildInstance(target, anim)
                instance.inTransitionTime = blendTime
                instance.outTransitionTime = blendTime
                instance.blendViaShortestPath = blendViaShortestPath
                instance.group = controller.animGroup
                applyWeightExpression(instance)
                instance.enter()  // enter() 内部自动调用 target.animController.playAnimation(this)
                controller.activeAnimInstances.add(instance)
            }
        }
        is AnimStateMachine -> {
            // 原有 1:1 逻辑不变（保持现有 AnimGroups.LOCOMOTION 默认值）
            val target = controller.animatable
            val instance = animInstance(target, animName) ?: return
            instance.inTransitionTime = blendTime
            instance.outTransitionTime = blendTime
            instance.blendViaShortestPath = blendViaShortestPath
            instance.group = AnimGroups.LOCOMOTION
            applyWeightExpression(instance)
            instance.enter()  // enter() 内部自动调用 target.animController.playAnimation(this)
            controller.activeAnimInstances.add(instance)
        }
        else -> warn("PlayAnimAction 只能在 AnimStateMachine / MultiAnimStateMachine 上下文中执行")
    }
}
```

---

## 5. MoLang 上下文与接口组合

### 5.1 现有扫描机制

`MolangContextRegistry.getOrCreateEngine()` 通过 `MochaEngine` 内部反射扫描 `@QueryBinding` 注解。反射使用 `Class.getMethods()` 获取所有 public 方法，**包括从接口继承的 default 方法**。Java 21 的 JVM 验证器接受 `INVOKEVIRTUAL` 调用接口 default 方法（JEP 181，自 Java 11 起）。

结论：**接口 default 方法 + `@QueryBinding` 完全可用。**

### 5.2 接口组合模式

将 `ctrl.*` 绑定按功能域拆分为独立接口，通过 `implements` 组合到任意 Context 子类：

```java
/**
 * ctrl 命名空间下的核心角色状态绑定。
 * 通过接口 default 方法 + @QueryBinding 组合到任意 Context。
 *
 * 使用：class MechaMolangContext extends SparkMolangContext implements MechaCtrlCoreBindings
 */
public interface MechaCtrlCoreBindings {

    StateVariableContainer getVariables();

    @QueryBinding(value = "is_on_ground", namespace = "ctrl")
    default double ctrlOnGround() {
        var v = getVariables();
        return v != null && v.get(StateVariableKeys.ON_GROUND) ? 1 : 0;
    }

    @QueryBinding(value = "speed", namespace = "ctrl")
    default double ctrlSpeed() {
        var v = getVariables();
        return v != null ? v.get(StateVariableKeys.SPEED) : 0;
    }

    @QueryBinding(value = "is_sprinting", namespace = "ctrl")
    default double ctrlSprinting() {
        var v = getVariables();
        return v != null && v.get(StateVariableKeys.IS_SPRINTING) ? 1 : 0;
    }

    @QueryBinding(value = "is_in_water", namespace = "ctrl")
    default double ctrlInWater() {
        var v = getVariables();
        return v != null && v.get(StateVariableKeys.IS_SWIMMING) ? 1 : 0;
    }

    @QueryBinding(value = "is_dead", namespace = "ctrl")
    default double ctrlDead() {
        var v = getVariables();
        return v != null && v.get(StateVariableKeys.IS_DEAD) ? 1 : 0;
    }

    @QueryBinding(value = "input_forward", namespace = "ctrl")
    default double ctrlInputForward() {
        var v = getVariables();
        return v != null ? v.get(StateVariableKeys.INPUT_FORWARD) : 0;
    }

    @QueryBinding(value = "input_strafe", namespace = "ctrl")
    default double ctrlInputStrafe() {
        var v = getVariables();
        return v != null ? v.get(StateVariableKeys.INPUT_STRAFE) : 0;
    }

    @QueryBinding(value = "all_animations_finished", namespace = "ctrl")
    default double ctrlAllAnimFinished() {
        var v = getVariables();
        return v != null && v.get(StateVariableKeys.ALL_ANIMATIONS_FINISHED) ? 1 : 0;
    }

    @QueryBinding(value = "any_animation_finished", namespace = "ctrl")
    default double ctrlAnyAnimFinished() {
        var v = getVariables();
        return v != null && v.get(StateVariableKeys.ANY_ANIMATION_FINISHED) ? 1 : 0;
    }
}
```

### 5.3 使用方式

```java
// ARMS-Core
public class MechaMolangContext extends SparkMolangContext<MechaEntity>
        implements MechaCtrlCoreBindings {

    private StateVariableContainer variables;

    @Override
    public StateVariableContainer getVariables() { return variables; }

    public void setVariables(StateVariableContainer v) { this.variables = v; }
}
```

### 5.4 对比继承

| | 继承（一个父类） | 接口组合 |
|---|---|---|
| 复用性 | 只继承一个类 | 实现多个接口 |
| 模块化 | 所有绑定写在一个类里 | 按域拆分（core / combat / build） |
| Mock | 需完整上下文 | 仅实现所需接口 |
| 发现 | `getMethods()` 直接覆盖 | `getMethods()` 覆盖（Java 21 兼容） |
| 与现有 SparkMolangContext 兼容 | 是 | 是 |

接口本身定义在各功能模组（如 ARMS-Core），不在 Spark-Core。

---

## 6. 实施步骤

### 步骤 1：类型化黑板

| 文件 | 操作 |
|------|------|
| `graph/StateVariableKey.kt` | 新建 |
| `graph/StateVariableContainer.kt` | 新建 |
| `presets/StateVariableKeys.kt` | 新建 |

### 步骤 2：挂载到 StateGraphController

| 文件 | 改动 |
|------|------|
| `graph/StateGraphController.kt` | `+ val variables = StateVariableContainer()`；`reset()` 中调用 `variables.clear()` |

### 步骤 3：MultiAnimStateMachine

| 文件 | 操作 |
|------|------|
| `animation/state/MultiAnimStateMachine.kt` | 新建（`builtinAnimations: OAnimationSet?` 构造参数；`animGroup` 默认 `AMBIENT`；`onCheckTransition` 仅迭代 `activeAnimInstances`） |

### 步骤 4：PlayAnimAction 分路

| 文件 | 改动 |
|------|------|
| `animation/state/actions/PlayAnimAction.kt` | `execute()` 改为 `when(controller)` 分路 |

### 步骤 5：MoLangCondition 改造

| 文件 | 改动 |
|------|------|
| `graph/conditions/MoLangCondition.kt` | `check()` 改为 `when(controller)` 分路：`AnimStateMachine` → 保持现有 `evalAsBoolean(animatable)` 路径；`MultiAnimStateMachine` → 走 `MolangContextRegistry.compile(value, ctx).evaluate(ctx) > 0.0` 原始求解路径 |

**说明**：广播状态机不需要 `q.anim_time`（状态机无动画时间概念），直接用 `MolangContext` 求值即可，无需通过 `IAnimatable` / `AnimInstance` 桥接。

### 步骤 6：MoLang 接口组合（ARMS-Core 侧，非 Spark-Core）

| 文件 | 操作 |
|------|------|
| `MechaCtrlCoreBindings.java` | 新建（接口 + default 方法 + `@QueryBinding`） |
| `MechaMolangContext.java` | 改为 `implements MechaCtrlCoreBindings` |

---

## 7. 不变部分

- `GameplayTagContainer` — 保留，与 `StateVariableContainer` 互补
- `HasTagCondition` — 条件求值逻辑不变
- `AnimStateMachine` — 完全不需修改
- `@QueryBinding` — 已有 `namespace` 参数，无需变更
- 所有现有 `OAnimState*` JSON 解析 — 不变

## 8. 线程说明

动画更新在物理线程执行，与 `IAnimatable.putVariable()` 一致。`StateVariableContainer` 的读写全部发生在物理线程，内部使用普通 `MutableMap` 无需同步。`GameplayTagContainer` 同样在物理线程操作，二者一致。