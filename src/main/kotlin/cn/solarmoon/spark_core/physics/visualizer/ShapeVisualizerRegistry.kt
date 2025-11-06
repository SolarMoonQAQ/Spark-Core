package cn.solarmoon.spark_core.physics.visualizer

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import kotlin.reflect.KClass

object ShapeVisualizerRegistry {

    private val registry = mutableMapOf<KClass<out CollisionShape>, () -> ShapeVisualizer>()

    init {
        register(BoxCollisionShape::class, ::BoxVisualizer)
        register(SphereCollisionShape::class, ::SphereVisualizer)
        register(CylinderCollisionShape::class, ::CylinderVisualizer)
        register(CapsuleCollisionShape::class, ::CapsuleVisualizer)
    }

    fun register(c: KClass<out CollisionShape>, s: () -> ShapeVisualizer) {
        registry[c] = s
    }

    fun getVisualizer(shape: CollisionShape): ShapeVisualizer? {
        return registry[shape::class]?.invoke()
    }

}