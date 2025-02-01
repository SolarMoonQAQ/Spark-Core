package cn.solarmoon.spark_core.physics.visualizer

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import kotlin.reflect.KClass

object ShapeVisualizerRegistry {
    private val registry = mutableMapOf<KClass<out CollisionShape>, () -> ShapeVisualizer>().apply {
        put(BoxCollisionShape::class, ::BoxVisualizer)
    }

    fun getVisualizer(shape: CollisionShape): ShapeVisualizer? {
        return registry[shape::class]?.invoke()
    }
}