package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.neoforged.neoforge.event.level.LevelEvent
/**
 * 在物理世界完全完成初始化后触发
 */
class PhysicsLevelInitEvent(val level: PhysicsLevel): LevelEvent(level.mcLevel) {}