package cn.solarmoon.spark_core.gas

/**
 * ### 技能
 * 描述技能运行时状态，实际上可以类比于[net.minecraft.world.entity.Entity]
 *
 * 技能通过[onEvent]进行逻辑通信，并不带由tick驱动的生命周期，具体逻辑通过[AbilityTask]完成，这样方便对逻辑进行拆分和组装
 */
abstract class Ability {

    val tasks = mutableListOf<AbilityTask>()

    open fun canActivate(spec: AbilitySpec<*>, context: ActivationContext) = true

    open fun activate(spec: AbilitySpec<*>, context: ActivationContext) {}

    /**
     * 不要手动触发这个end，真正结束请通过[AbilitySpec.endAbility]
     * @param wasCancelled 是否被打断而不是正常结束
     */
    open fun end(spec: AbilitySpec<*>, wasCancelled: Boolean) {}

    open fun onEvent(spec: AbilitySpec<*>, event: AbilityEvent) {}

    fun readyForActivation(task: AbilityTask) {
        tasks += task
        task.activate()
    }

    fun removeTask(task: AbilityTask) {
        tasks.remove(task)
    }

    fun endAllTasks(ownerFinished: Boolean) {
        tasks.toList().forEach { it.end(ownerFinished) }
        tasks.clear()
    }

}