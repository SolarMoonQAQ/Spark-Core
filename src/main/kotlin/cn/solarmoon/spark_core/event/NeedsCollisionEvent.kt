package cn.solarmoon.spark_core.event

import com.jme3.bullet.collision.PhysicsCollisionObject
import net.neoforged.bus.api.Event

class NeedsCollisionEvent(
    val pcoA: PhysicsCollisionObject,
    val pcoB: PhysicsCollisionObject,
    origin: Boolean
): Event() {

    var shouldCollide = origin

}