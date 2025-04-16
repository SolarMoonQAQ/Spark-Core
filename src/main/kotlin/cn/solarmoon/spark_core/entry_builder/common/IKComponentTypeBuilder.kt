package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.ik.component.IKComponentType
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/**
 * Builder for registering IKComponentType instances using DeferredRegister.
 */
class IKComponentTypeBuilder(private val register: DeferredRegister<IKComponentType>) {

    fun build(name: String, supplier: Supplier<IKComponentType>): Supplier<IKComponentType> {
        return register.register(name, supplier)
    }
}
