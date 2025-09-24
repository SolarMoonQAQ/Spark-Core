package cn.solarmoon.spark_core.physics.component

import cn.solarmoon.spark_core.physics.ManifoldPoint
import cn.solarmoon.spark_core.util.InlineEvent
import com.jme3.bullet.collision.PhysicsCollisionObject

abstract class CollisionObjectEvent: InlineEvent {

    abstract class Collide(
        val o1: PhysicsCollisionObject,
        val o2: PhysicsCollisionObject,
        val o1Point: ManifoldPoint,
        val o2Point: ManifoldPoint,
    ): CollisionObjectEvent() {
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

    class AddToWorld: CollisionObjectEvent()

    object Tick: CollisionObjectEvent()

    abstract class PhysicsTick: CollisionObjectEvent() {
        class Pre: PhysicsTick()
        class Post: PhysicsTick()
    }

}