package cn.solarmoon.spark_core.entry_builder

import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister


abstract class RegisterBuilder<R, C : R>(
    val deferredRegister: DeferredRegister<R>
) {

    val modId get() = deferredRegister.namespace

    lateinit var id: String

    open fun validate() {
        if (!this::id.isInitialized) {
            throw IllegalStateException("未给 ${javaClass.simpleName} 指定id!")
        }
    }

    abstract fun build(): DeferredHolder<R, C>

}
