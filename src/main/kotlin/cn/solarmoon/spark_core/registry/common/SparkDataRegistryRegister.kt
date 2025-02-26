package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.skill.SkillType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DataPackRegistryEvent

object SparkDataRegistryRegister {

    private fun reg(event: DataPackRegistryEvent.NewRegistry) {
        event.dataPackRegistry(SparkRegistries.SKILL_TYPE, SkillType.CODEC, SkillType.CODEC)
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}