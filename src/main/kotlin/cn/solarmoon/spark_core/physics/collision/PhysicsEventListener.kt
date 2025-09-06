package cn.solarmoon.spark_core.physics.collision

fun interface PhysicsEventListener<T : PhysicsObjectEvent> {
    fun handle(event: T)
}