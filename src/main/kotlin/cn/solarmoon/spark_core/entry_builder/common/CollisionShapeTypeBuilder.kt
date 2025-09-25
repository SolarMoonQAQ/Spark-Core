package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.physics.component.CollisionObjectComponent
import cn.solarmoon.spark_core.physics.component.shape.CollisionShapeType
import com.jme3.bullet.collision.shapes.CollisionShape
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class CollisionShapeTypeBuilder<C: CollisionShape>(
    private val collisionShapeDeferredRegister: DeferredRegister<CollisionShapeType<*>>
) {

    private var id = ""
    private var provider: ((CollisionObjectComponent<*>) -> C)? = null

    fun id(id: String) = apply {
        this.id = id
    }

    fun bound(provider: (CollisionObjectComponent<*>) -> C) = apply {
        this.provider = provider
    }

    fun build(): DeferredHolder<CollisionShapeType<*>, CollisionShapeType<C>> {
        return collisionShapeDeferredRegister.register(id, Supplier { CollisionShapeType(provider!!) })
    }

}