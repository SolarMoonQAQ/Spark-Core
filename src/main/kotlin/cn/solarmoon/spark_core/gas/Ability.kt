package cn.solarmoon.spark_core.gas

/**
 * ### 技能
 * 描述技能运行时状态，实际上可以类比于[net.minecraft.world.entity.Entity]
 *
 * 技能通过[onEvent]进行逻辑通信，并不带由tick驱动的生命周期，具体逻辑通过[AbilityTask]完成，这样方便对逻辑进行拆分和组装
 */
abstract class Ability {

    val tasks = mutableListOf<AbilityTask>()

    open fun canActivate(spec: AbilitySpec<*>, context: ActivationContext): ActivationResult = ActivationResult(true)

    open fun activate(spec: AbilitySpec<*>, context: ActivationContext) {}

    open fun end(spec: AbilitySpec<*>,) {}

    open fun onEvent(spec: AbilitySpec<*>, event: AbilityEvent) {}

    fun addTask(task: AbilityTask) {
        tasks += task
        task.start()
    }

    fun removeTask(task: AbilityTask) {
        tasks.remove(task)
        task.stop()
    }

}