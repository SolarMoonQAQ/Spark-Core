package cn.solarmoon.spark_core.physics.body

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Transform

class PhysicsBodyState(
    private val body: PhysicsCollisionObject
) {

    var lastTransform = body.getTransform(null)// 上一帧的刚体变换，用于插值
    var transform = body.getTransform(null)// 最新的刚体变换
    var cachedBoundingBox = body.boundingBox(null)// 缓存的刚体包围盒，主线程需要获取刚体包围盒时，可从此获取避免竞态和重复计算

    fun update() {
        lastTransform = transform
        transform = body.getTransform(null)
        cachedBoundingBox = body.boundingBox(null)
    }

}