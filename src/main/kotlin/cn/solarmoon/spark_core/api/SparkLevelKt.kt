package cn.solarmoon.spark_core.api

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.terrain.ChunkHeightIndex
import cn.solarmoon.spark_core.util.PPhase
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
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

// ========== 区块实体方块高程索引公开 API ==========
// 所有实现委托到 SparkLevel.java 的 static 方法，确保 Java 端同样可用

/**
 * 获取此 Level 的区块固体高度索引（若不存在则返回 null）。
 * 客户端始终返回 null（索引仅服务端维护）。
 */
val Level.chunkHeightIndex: ChunkHeightIndex?
    get() = SparkLevel.getChunkHeightIndex(this)

/**
 * 查询指定区块位置的指定 Y 区间内是否存在实体方块。
 *
 * 线程安全：可在任意线程调用（不访问 MC 世界状态）
 */
fun Level.hasSolidInRange(pos: BlockPos, yMin: Double, yMax: Double): Boolean =
    SparkLevel.hasSolidInRange(this, pos, yMin, yMax)

/**
 * 查询指定区块的完整固体区间列表。
 * 调用方不得修改返回的数组（共享引用，线程安全）。
 */
fun Level.getSolidIntervals(chunkPos: ChunkPos): ShortArray? =
    SparkLevel.getSolidIntervals(this, chunkPos)

/**
 * 检查指定区块是否已建立索引（即曾被加载过）。
 */
fun Level.hasChunkIndex(chunkPos: ChunkPos): Boolean =
    SparkLevel.hasChunkIndex(this, chunkPos)

/**
 * 预约在指定延迟后加载指定区块的指定 Y 范围地形，并在保持一定 tick 后自动释放。
 *
 * 多次调度同一 chunk 取最晚过期（自动合并）。
 * 调用方无需手动 release，系统在到期后自动释放。
 *
 * 调用线程：任意线程（内部自动投递到主线程处理 chunk 加载）
 */
fun Level.scheduleChunkLoad(
    chunkPos: ChunkPos,
    yMin: Double,
    yMax: Double,
    delayTicks: Int,
    holdTicks: Int
) {
    SparkLevel.scheduleChunkLoad(this, chunkPos, yMin, yMax, delayTicks, holdTicks)
}

/**
 * 检查指定区块的指定 Y 范围地形是否已就绪（已加载 + 已构建 + 已激活）。
 */
fun Level.isChunkTerrainReady(chunkPos: ChunkPos, yMin: Double, yMax: Double): Boolean =
    SparkLevel.isChunkTerrainReady(this, chunkPos, yMin, yMax)

/**
 * 取消对某区块的自动释放调度（投射物销毁时可选调用）。
 * 仅取消到期自动卸载的定时器，不立即释放物理区块。
 */
fun Level.cancelChunkLoad(chunkPos: ChunkPos) {
    SparkLevel.cancelChunkLoad(this, chunkPos)
}
