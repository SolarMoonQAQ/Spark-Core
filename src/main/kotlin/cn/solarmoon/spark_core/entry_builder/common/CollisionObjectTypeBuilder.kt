package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.physics.component.Authority
import cn.solarmoon.spark_core.physics.component.CollisionObjectComponent
import cn.solarmoon.spark_core.physics.component.CollisionObjectType
import com.jme3.bullet.collision.shapes.CollisionShape
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class CollisionObjectTypeBuilder<T: CollisionObjectComponent<*>>(private val registry: DeferredRegister<CollisionObjectType<*>>, private val modId: String) {

    private var id: String = ""
    private lateinit var rulesSupplier: (String, Authority, CollisionShape, Level) -> T

    fun id(id: String) = apply { this.id = id }

    fun bound(provider: (String, Authority, CollisionShape, Level) -> T) = apply {
        this.rulesSupplier = provider
    }

    fun build() = registry.register(id, Supplier { CollisionObjectType(ResourceLocation.fromNamespaceAndPath(modId, id), rulesSupplier) })

}