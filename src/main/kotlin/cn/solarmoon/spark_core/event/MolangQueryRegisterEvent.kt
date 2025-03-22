package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.molang.core.builtin.QueryBinding
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

class MolangQueryRegisterEvent(
    val binding: QueryBinding
): Event(), IModBusEvent {



}