package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.util.InlineEvent
import com.jme3.bullet.collision.PhysicsCollisionObject

abstract class PhysicsBodyEvent: InlineEvent {

    abstract class Collide(
        val o1: PhysicsCollisionObject,
        val o2: PhysicsCollisionObject,
        val o1Point: ManifoldPoint,
        val o2Point: ManifoldPoint,
    ): PhysicsBodyEvent() {
        class Started(
            o1: PhysicsCollisionObject,
            o2: PhysicsCollisionObject,
            o1Point: ManifoldPoint,
            o2Point: ManifoldPoint,
        ): Collide(o1, o2, o1Point, o2Point)

        class Processed(
            o1: PhysicsCollisionObject,
            o2: PhysicsCollisionObject,
            o1Point: ManifoldPoint,
            o2Point: ManifoldPoint,
        ): Collide(o1, o2, o1Point, o2Point)

        class Ended(
            o1: PhysicsCollisionObject,
            o2: PhysicsCollisionObject,
            o1Point: ManifoldPoint,
            o2Point: ManifoldPoint,
        ): Collide(o1, o2, o1Point, o2Point)
    }

    class AddToWorld: PhysicsBodyEvent()

    object Tick: PhysicsBodyEvent()

    abstract class PhysicsTick: PhysicsBodyEvent() {
        class Pre: PhysicsTick()
        class Post: PhysicsTick()
    }

}