package cn.solarmoon.spark_core.physics.level

import com.jme3.bullet.collision.PhysicsCollisionObject

class CollisionManager {

    // 存储碰撞对的双缓冲集合（使用有序 Pair 避免重复）
    private val previousCollisions = mutableSetOf<Pair<Long, Long>>()
    private val currentCollisions = mutableSetOf<Pair<Long, Long>>()
    val manifolds = mutableMapOf<Pair<Long, Long>, MutableSet<Long>>()

    fun addCollision(ids: Pair<Long, Long>, manifold: Long) {
        currentCollisions.add(ids)
        manifolds.getOrPut(ids) { mutableSetOf() }.add(manifold)
    }

    fun prePhysicsTick() {
        // 清空当前帧数据，保留上一帧数据用于比较
        previousCollisions.clear()
        previousCollisions.addAll(currentCollisions)
        currentCollisions.clear()
    }

    fun physicsTick() {
        // 处理碰撞状态变化事件
        val newCollisions = currentCollisions - previousCollisions
        val endedCollisions = previousCollisions - currentCollisions

        newCollisions.forEach { (idA, idB) ->
            val manifold = manifolds[idA to idB]?.first() ?: return@forEach
            val a = PhysicsCollisionObject.findInstance(idA)
            val b = PhysicsCollisionObject.findInstance(idB)
            if (a != null && b != null) {
                if (a.isCollisionGroupContains(b)) a.collisionListeners.forEach { a.isColliding = true; it.onStarted(a, b, manifold) }
                if (b.isCollisionGroupContains(a)) b.collisionListeners.forEach { b.isColliding = true; it.onStarted(b, a, manifold) }
            }
        }

        endedCollisions.forEach { (idA, idB) ->
            val manifoldGroup = manifolds[idA to idB] ?: return@forEach
            val manifold = manifoldGroup.last()
            manifoldGroup.clear()
            val a = PhysicsCollisionObject.findInstance(idA)
            val b = PhysicsCollisionObject.findInstance(idB)
            if (a != null && b != null) {
                a.collisionListeners.forEach { a.isColliding = false; it.onEnded(a, b, manifold) }
                b.collisionListeners.forEach { b.isColliding = false; it.onEnded(b, a, manifold) }
            }
        }
    }

}