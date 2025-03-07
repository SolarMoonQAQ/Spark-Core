package cn.solarmoon.spark_core.physics.collision

fun interface PhysicsEventListener<T : PhysicsEvent> {
    fun handle(event: T)
}