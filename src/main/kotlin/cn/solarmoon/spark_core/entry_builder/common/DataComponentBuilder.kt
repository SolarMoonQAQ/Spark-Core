package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.core.component.DataComponentType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents

class DataComponentBuilder<D>(private val dataComponentDeferredRegister: DataComponents) {//

    private var id = ""

    fun id(id: String) = apply { this.id = id }

    fun build(provider: (DataComponentType. Builder<D>) -> Unit): DeferredHolder<DataComponentType<*>, DataComponentType<D>> {
        return dataComponentDeferredRegister.registerComponentType(id) { builder ->
            provider.invoke(builder)
            builder
        }
    }

}