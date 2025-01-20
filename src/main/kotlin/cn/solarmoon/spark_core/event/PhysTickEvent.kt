package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.phys.thread.PhysLevel
import net.neoforged.bus.api.Event

abstract class PhysTickEvent(val level: PhysLevel): Event() {

    class Level(level: PhysLevel): PhysTickEvent(level)

    class Entity(val entity: net.minecraft.world.entity.Entity, level: PhysLevel): PhysTickEvent(level)

}