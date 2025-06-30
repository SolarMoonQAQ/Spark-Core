package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/**
 * Builder for registering TypedIKComponent instances using DeferredRegister.
 */
class IKComponentTypeBuilder(private val register: DeferredRegister<TypedIKComponent>) {

    fun build(name: String, supplier: Supplier<TypedIKComponent>): Supplier<TypedIKComponent> {
        return register.register(name, supplier)
    }
}
