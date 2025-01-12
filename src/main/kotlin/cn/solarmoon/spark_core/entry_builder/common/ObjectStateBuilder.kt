package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.state_control.ObjectState
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class ObjectStateBuilder<T>(
    private val modId: String,
    private val stateTypeDeferredRegister: DeferredRegister<ObjectState<*>>
) {

    private var id: String = ""
    private var condition: ((T) -> Boolean)? = null

    fun id(id: String) = apply { this.id = id }
    fun condition(condition: (T) -> Boolean) = apply { this.condition = condition }

    fun build() = stateTypeDeferredRegister.register(id, Supplier { ObjectState<T>(id, ResourceLocation.fromNamespaceAndPath(modId, id), condition!!) })

}