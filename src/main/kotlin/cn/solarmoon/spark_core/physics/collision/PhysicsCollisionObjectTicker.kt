package cn.solarmoon.spark_core.physics.collision

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.level.Level

interface PhysicsCollisionObjectTicker {

    fun physicsTick(body: PhysicsCollisionObject, level: PhysicsLevel) {}

    fun mcTick(body: PhysicsCollisionObject, level: Level) {}

}