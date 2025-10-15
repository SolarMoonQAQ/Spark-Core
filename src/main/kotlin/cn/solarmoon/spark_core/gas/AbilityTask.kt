package cn.solarmoon.spark_core.gas

/**
 * ### 技能任务
 * 技能任务为技能逻辑的子模块
 *
 * 其目的是
 * - 封装子流程逻辑
 * - 与运行时状态解耦（可拆分为多个任务，在需要时组装调用）
 *
 * 技能任务通过[AbilityEvent]与技能运行时状态进行通信，由[Ability.onEvent]执行具体逻辑
 */
abstract class AbilityTask(
    val spec: AbilitySpec<*>
) {

    var isFinished: Boolean = false
        private set

    open fun activate() {}

    open fun tick() {}

    /**
     * 不要手动调用，请走[end]
     * @param ownerFinished 是否因为 Ability 整体结束而销毁
     */
    open fun onDestroy(ownerFinished: Boolean) {}

    fun end(ownerFinished: Boolean) {
        if (isFinished) return
        isFinished = true

        // 调用子类清理逻辑
        onDestroy(ownerFinished)

        // 从 Ability 移除自己
        spec.activeAbilities.forEach { ability ->
            ability.removeTask(this)
        }
    }

}

