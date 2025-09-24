package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.delta_sync.generateSchema
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.registries.DeferredRegister
import kotlin.reflect.KClass

class DeltaSyncRulesBuilder<T: Any>(private val registry: DeferredRegister<DiffSyncSchema<*>>, private val modId: String, private val type: KClass<T>) {

    private var id: String = ""
    private lateinit var rulesSupplier: () -> DiffSyncSchema<T>

    fun id(id: String) = apply { this.id = id }

    fun bound(rules: () -> DiffSyncSchema<T>) = apply {
        this.rulesSupplier = rules
    }

    fun customBound() = apply { this.rulesSupplier = { generateSchema(type, ResourceLocation.fromNamespaceAndPath(modId, id)) } }

    fun build() = registry.register(id, rulesSupplier)

}