package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.molang.core.builtin.QueryBinding
import net.neoforged.bus.api.Event

class MolangQueryRegisterEvent(
    val binding: QueryBinding
): Event() {



}