package cn.solarmoon.spark_core.entry_builder

import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister


open class RegisterBuilder<R, C : R>(
    val deferredRegister: DeferredRegister<R>
) {

    val modId get() = deferredRegister.namespace

    lateinit var id: String

    open lateinit var factory: () -> C

    open fun validate() {
        if (!this::id.isInitialized) {
            throw IllegalStateException("id must be initialized before build()")
        }
        if (!this::factory.isInitialized) {
            throw IllegalStateException("provider must be initialized before build()")
        }
    }

    open fun build(): DeferredHolder<R, C> {
        validate()
        return deferredRegister.register(id, factory)
    }

}
