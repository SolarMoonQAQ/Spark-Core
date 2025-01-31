package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.neoforged.bus.api.Event

abstract class PhysicsTickEvent(val level: PhysicsLevel): Event() {

    open class Level(level: PhysicsLevel): PhysicsTickEvent(level) {
        /**
         * 在物理步进之前调用，可在此时改变运动参数
         */
        class Pre(level: PhysicsLevel): Level(level)
    }

    class Entity(val entity: net.minecraft.world.entity.Entity, level: PhysicsLevel): PhysicsTickEvent(level)

}