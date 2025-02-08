package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.sync.SyncerType
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class SyncerTypeBuilder(private val value: DeferredRegister<SyncerType>) {

    private var id = ""
    private var bound: (() -> SyncerType)? = null

    fun id(id: String) = apply { this.id = id }

    fun bound(type: () -> SyncerType) = apply { this.bound = type }

    fun build() = value.register(id, Supplier { bound!!.invoke() })

}