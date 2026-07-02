package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.molang.SparkMolangContext
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateNode
import cn.solarmoon.spark_core.state_machine.presets.StateVariableKeys

/**
 * 多模型动画状态机 —— 1:N 广播模式。
 *
 * 每个 [animTargets] 中的 target（如机娘的各个 SubPart）通过三级回退查找同名动画：
 * 1. target 自己的 [OAnimationSet]
 * 2. [fallbackAnimations]（素体/MechaControl 提供，每个机甲实例不同）
 * 3. [builtinAnimations]（Mod 内置动画集，由下游模组构造时传入已加载实例）
 *
 * 动画完成状态仅检查本机播放的动画（[activeAnimInstances]），
 * 不遍历 target 的所有 layer，避免被其他控制器干扰。
 *
 * [contextProvider] 提供 MoLang 上下文（通常来自宿主实体），
 * 用于 [cn.solarmoon.spark_core.state_machine.graph.conditions.MoLangCondition] 求值转移条件。
 *
 * @param graph 状态机图定义
 * @param animTargets 广播目标列表（如机甲的所有 SubPart）
 * @param contextProvider MoLang 上下文提供者（来自宿主实体，而非某个零件）
 * @param animGroup 写入的目标动画层，默认 [AnimGroups.AMBIENT]
 * @param fallbackAnimations 实例级默认动画集（素体提供，每个机甲不同），可为空
 * @param builtinAnimations Mod 内置动画集（由下游模组构造时传入已加载实例），可为空
 * @param children 子控制器映射
 */
open class MultiAnimStateMachine(
    graph: StateMachineGraph,
    /** 广播目标列表 */
    val animTargets: List<IAnimatable<*>>,
    /** MoLang 上下文提供者（来自宿主实体，而非某个零件） */
    val contextProvider: () -> SparkMolangContext<*>,
    /** 实例级默认动画集（素体提供，每个机甲不同），可为空 */
    var fallbackAnimations: OAnimationSet? = null,
    /** Mod 内置默认动画集（由下游模组构造时传入已加载实例），可为空 */
    var builtinAnimations: OAnimationSet? = null,
    /** 写入的目标动画层，默认 [AnimGroups.AMBIENT] */
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
        target.animController.originAnimations.getAnimation(animName)?.let { return it }
        fallbackAnimations?.getAnimation(animName)?.let { return it }
        builtinAnimations?.getAnimation(animName)?.let { return it }
        return null
    }

    /**
     * 在每帧转移条件求值前调用，检查本机播放的动画完成状态。
     * 与 [AnimStateMachine] 一致：仅迭代 [activeAnimInstances]。
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
        // 清理动画追踪（移除 Tick handler，防止退出过渡期间覆写 weight）
        for (inst in activeAnimInstances) {
            inst.eventHandlers.remove(AnimEvent.Tick::class)
        }
        activeAnimInstances.clear()
        super.onExit(node)
    }
}
