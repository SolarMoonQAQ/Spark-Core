package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateNode

/**
 * 动画状态机控制器。
 * 继承 [StateGraphController]，增加动画生命周期追踪和 MoLang 上下文注入能力。
 *
 * 职责：
 * 1. 追踪当前状态活跃的 [AnimInstance] 列表（由 [cn.solarmoon.spark_core.state_machine.graph.actions.PlayAnimAction] 维护）
 * 2. 在转移求值前更新动画完成状态 → 注入 MoLang 上下文（`q.all_animations_finished`）
 *
 * 子控制器管理由父类 [StateGraphController] 提供，AnimStateMachine 不关心。
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
        animatable.controllerAllAnimationsFinished = allDone
        animatable.controllerAnyAnimationFinished = anyDone
    }

    override fun onExit(node: StateNode) {
        // 先清理动画追踪（移除 Tick handler，防止退出过渡期间覆写 weight）
        for (inst in activeAnimInstances) {
            inst.eventHandlers.remove(AnimEvent.Tick::class)
        }
        activeAnimInstances.clear()

        // 再调父类 onExit → 递归清理子控（含孙子）
        super.onExit(node)
    }

}
