package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.sync.Syncer
import cn.solarmoon.spark_core.sync.SyncerType
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class SyncerTypeBuilder<T: Syncer>(private val value: DeferredRegister<SyncerType<*>>) {

    private var id = ""
    private var bound: (() -> SyncerType<T>)? = null

    fun id(id: String) = apply { this.id = id }

    fun bound(type: () -> SyncerType<T>) = apply { this.bound = type }

    fun build() = value.register(id, Supplier { bound!!.invoke() })

}