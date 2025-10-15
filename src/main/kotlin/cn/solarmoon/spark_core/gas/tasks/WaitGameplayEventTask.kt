package cn.solarmoon.spark_core.gas.tasks

import cn.solarmoon.spark_core.gas.AbilityEvent
import cn.solarmoon.spark_core.gas.AbilitySpec
import cn.solarmoon.spark_core.gas.AbilityTask
import cn.solarmoon.spark_core.gas.GameplayTag

class WaitGameplayEventTask(
    spec: AbilitySpec<*>,
    private val tag: GameplayTag,
    private val oneTriggered: Boolean = true,
    private val onEvent: (AbilityEvent) -> Unit
) : AbilityTask(spec) {

    override fun activate() {
        spec.asc.registerListener(tag, spec) { event ->
            onEvent(event)
            // 默认一次性任务，触发后自动结束
            if (oneTriggered) end(false)
        }
    }

    override fun onDestroy(ownerFinished: Boolean) {
        spec.asc.unregisterListener(tag, spec)
    }

}
