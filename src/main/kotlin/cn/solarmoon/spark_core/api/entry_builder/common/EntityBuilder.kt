package cn.solarmoon.spark_core.api.entry_builder.common

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier


class EntityBuilder<E: Entity>(private val entityDeferredRegister: DeferredRegister<EntityType<*>>) {

    private var id = ""
    private var builder: EntityType.Builder<E>? = null

    fun id(id: String) = apply { this.id = id }

    fun builder(builder: EntityType.Builder<E>) = apply { this.builder = builder }

    fun build(): DeferredHolder<EntityType<*>, EntityType<E>> {
        return entityDeferredRegister.register(id, Supplier { builder!!.build(id) })
    }

}