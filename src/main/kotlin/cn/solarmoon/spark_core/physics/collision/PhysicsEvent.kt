package cn.solarmoon.spark_core.physics.collision

abstract class PhysicsEvent {

    object OnCollisionActive: PhysicsEvent()

    object OnCollisionInactive: PhysicsEvent()

}