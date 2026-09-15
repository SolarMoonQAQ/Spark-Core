package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.neoforged.bus.api.Event

/**
 * 物理世界快照刷新完成事件（主线程）。
 *
 * 触发点位于 [PhysicsLevel.requestStep] 内部，在
 * `syncStructure` / `syncTransform` / `update` 全部走完、物理线程被唤醒之前，
 * 因此**只在快照确实完成刷新时触发**——`requestStep` 因物理线程仍在运行而提前返回时不会触发。
 *
 * 语义约定（供订阅者使用）：
 * - **事件即一步**：收到一次事件即代表快照前进了一步，订阅者按"一次事件 = 推进一次"处理即可，
 *   不需要额外的待排队步数计数器，也不需要新鲜度标志。
 * - 事件率与快照刷新率一致；物理线程过载导致快照被跳过时，事件随之变稀。
 *
 * @param physicsLevel 本次刷新的物理世界
 * @param physicsStep  快照步号（主线程 tick 计数），仅作事件丢失诊断用
 */
class PhysicsSnapshotReadyEvent(
    val physicsLevel: PhysicsLevel,
    val physicsStep: Long,
) : Event()
