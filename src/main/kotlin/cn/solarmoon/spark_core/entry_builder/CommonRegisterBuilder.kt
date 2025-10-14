package cn.solarmoon.spark_core.entry_builder

import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

open class CommonRegisterBuilder<R, C: R>(
    deferredRegister: DeferredRegister<R>
): RegisterBuilder<R, C>(deferredRegister) {

    open lateinit var factory: () -> C

    override fun validate() {
        super.validate()
        if (!this::factory.isInitialized) {
            throw IllegalStateException("未给 ${javaClass.simpleName} 指定构造函数!")
        }
    }

    override fun build(): DeferredHolder<R, C> {
        validate()
        return deferredRegister.register(id, factory)
    }

}