package cn.solarmoon.spark_core.gas

/**
 * ### 技能
 * 描述技能运行时状态，实际上可以类比于[net.minecraft.world.entity.Entity]
 *
 * 技能通过[onEvent]进行逻辑通信，并不带由tick驱动的生命周期，具体逻辑通过[AbilityTask]完成，这样方便对逻辑进行拆分和组装
 */
abstract class Ability(
    val spec: AbilitySpec<*>,
    val context: ActivationContext
) {

    val tasks = mutableListOf<AbilityTask>()

    /** 能否激活（检查冷却、资源、标签等） */
    open fun canActivate(): ActivationResult = ActivationResult(true)

    /** 激活时调用（运行时逻辑） */
    open fun activate() {}

    /** 技能结束时调用 */
    open fun end() {}

    /** 处理来自任务或外部的事件 */
    protected open fun onEvent(event: AbilityEvent) {}

    fun addTask(task: AbilityTask) {
        tasks += task
        task.start()
    }

    fun removeTask(task: AbilityTask) {
        tasks.remove(task)
        task.stop()
    }

    /** 发送事件 */
    fun emit(event: AbilityEvent) {
        onEvent(event)
    }

}