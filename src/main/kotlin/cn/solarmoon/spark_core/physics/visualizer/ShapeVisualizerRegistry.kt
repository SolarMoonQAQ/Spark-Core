package cn.solarmoon.spark_core.physics.visualizer

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import kotlin.reflect.KClass

object ShapeVisualizerRegistry {

    private val registry = mutableMapOf<KClass<out CollisionShape>, () -> ShapeVisualizer>()

    init {
        register(BoxCollisionShape::class, ::BoxVisualizer)
    }

    fun register(c: KClass<out CollisionShape>, s: () -> ShapeVisualizer) {
        registry[c] = s
    }

    fun getVisualizer(shape: CollisionShape): ShapeVisualizer? {
        return registry[shape::class]?.invoke()
    }

}