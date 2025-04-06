package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.skill.SkillType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DataPackRegistryEvent

object SparkDataRegistryRegister {

    private fun reg(event: DataPackRegistryEvent.NewRegistry) {
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}