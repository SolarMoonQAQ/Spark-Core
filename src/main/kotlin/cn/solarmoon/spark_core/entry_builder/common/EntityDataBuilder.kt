package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.syncher.EntityDataSerializer
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class EntityDataBuilder<D>(private val entityDataDeferredRegister: DeferredRegister<EntityDataSerializer<*>>) {//

    private var id = ""
    private var dataSerializer: (() -> EntityDataSerializer<D>)? = null

    fun id(id: String) = apply { this.id = id }

    fun bound(codec: StreamCodec<in RegistryFriendlyByteBuf, D>) = apply { this.dataSerializer = { EntityDataSerializer.forValueType(codec) } }

    fun build(): DeferredHolder<EntityDataSerializer<*>, EntityDataSerializer<D>> {
        return entityDataDeferredRegister.register(id, dataSerializer!!)
    }

}