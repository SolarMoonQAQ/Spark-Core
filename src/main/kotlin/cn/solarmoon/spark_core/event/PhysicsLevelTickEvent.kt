package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.neoforged.neoforge.event.level.LevelEvent

abstract class PhysicsLevelTickEvent(val level: PhysicsLevel): LevelEvent(level.mcLevel) {

    /**
     * 在物理步进之前调用，可在此时改变运动参数
     */
    class Pre(level: PhysicsLevel): PhysicsLevelTickEvent(level)

    class Post(level: PhysicsLevel): PhysicsLevelTickEvent(level)

}