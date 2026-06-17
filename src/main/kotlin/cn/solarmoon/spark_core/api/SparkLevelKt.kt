package cn.solarmoon.spark_core.api

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.PPhase
import net.minecraft.world.level.Level

/**
 * <p>获取 Level 的 PhysicsLevel。</p>
 * <p>Get the PhysicsLevel associated with this Level.</p>
 */
val Level.physicsLevel: PhysicsLevel
    get() = SparkLevel.getPhysicsLevel(this)

/**
 * <p>提交一个去重任务。</p>
 * <p>Submit a deduplicated task.</p>
 */
fun Level.submitDeduplicatedTask(
    key: String,
    phase: PPhase,
    task: () -> Unit
) {
    SparkLevel.submitDeduplicatedTask(
        this, key, phase, Runnable(task)
    )
}

/**
 * <p>提交一个即时任务。</p>
 * <p>Submit an immediate task.</p>
 */
fun Level.submitImmediateTask(
    phase: PPhase = PPhase.ALL,
    task: () -> Unit
) {
    SparkLevel.submitImmediateTask(
        this, phase, Runnable(task)
    )
}

/**
 * <p>提交一个去重延迟任务，将在 processTasks(phase) 被调用 [delayTicks] 次后执行。</p>
 * <p>Submit a deduplicated delayed task that executes after N processTasks(phase) calls.</p>
 *
 * <p>同一 phase + key 会覆盖旧的延迟任务（去重）。</p>
 * <p>Same phase + key will overwrite the previous delayed task (dedup).</p>
 */
fun Level.submitDelayedTask(
    key: String,
    phase: PPhase,
    delayTicks: Int,
    task: () -> Unit
) {
    SparkLevel.submitDelayedTask(
        this, key, phase, delayTicks, Runnable(task)
    )
}

/**
 * <p>提交一个非去重延迟任务，每次调用均新增，互不覆盖。</p>
 * <p>Submit a non-deduplicated delayed task; each call adds a new independent task.</p>
 */
fun Level.submitDelayedTask(
    phase: PPhase,
    delayTicks: Int,
    task: () -> Unit
) {
    SparkLevel.submitDelayedTask(
        this, phase, delayTicks, Runnable(task)
    )
}

/**
 * <p>处理指定阶段的任务。</p>
 * <p>Process tasks of the given phase.</p>
 */
fun Level.processTasks(phase: PPhase) {
    SparkLevel.processTasks(this, phase)
}
