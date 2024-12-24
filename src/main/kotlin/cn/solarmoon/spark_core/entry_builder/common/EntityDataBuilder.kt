package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.network.syncher.EntityDataSerializer
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class EntityDataBuilder<D>(private val entityDataDeferredRegister: DeferredRegister<EntityDataSerializer<*>>) {//

    private var id = ""
    private var dataSerializer: Supplier<EntityDataSerializer<D>>? = null

    fun id(id: String) = apply { this.id = id }

    fun builder(dataSerializer: Supplier<EntityDataSerializer<D>>) = apply { this.dataSerializer = dataSerializer }

    fun build(): DeferredHolder<EntityDataSerializer<*>, EntityDataSerializer<D>> {
        return entityDataDeferredRegister.register(id, dataSerializer!!)
    }

}