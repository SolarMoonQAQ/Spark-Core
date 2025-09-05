package cn.solarmoon.spark_core.physics.collision

abstract class PhysicsObjectEvent {

    object OnCollisionActive: PhysicsObjectEvent()

    object OnCollisionInactive: PhysicsObjectEvent()

}