package cn.solarmoon.spark_core.event

import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.event.entity.EntityEvent

class PhysicsEntityTickEvent(entity: Entity): EntityEvent(entity) {
}