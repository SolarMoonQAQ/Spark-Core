package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.skill.SkillGroup
import cn.solarmoon.spark_core.skill.SkillType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DataPackRegistryEvent

object SparkDataRegistryRegister {

    private fun reg(event: DataPackRegistryEvent.NewRegistry) {
        event.dataPackRegistry(SparkRegistries.SKILL_TYPE, SkillType.CODEC, SkillType.CODEC)
        event.dataPackRegistry(SparkRegistries.SKILL_GROUP, SkillGroup.CODEC, SkillGroup.CODEC)
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}