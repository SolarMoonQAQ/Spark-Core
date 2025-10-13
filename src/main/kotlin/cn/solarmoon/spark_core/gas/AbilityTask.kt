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
interface AbilityTask {

    val ability: Ability

    fun start() {}

    fun tick() {}

    fun stop() {}

}

