package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.phys.BodyType
import cn.solarmoon.spark_core.phys.thread.PhysLevel
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredRegister
import org.ode4j.ode.DBody
import java.util.function.Supplier

class BodyTypeBuilder(private val value: DeferredRegister<BodyType>) {

    private var id = ""

    fun id(id: String) = apply { this.id = id }

    fun build() = value.register(id, Supplier { BodyType() })

}