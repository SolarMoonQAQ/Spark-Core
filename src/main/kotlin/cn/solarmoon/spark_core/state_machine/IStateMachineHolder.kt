package cn.solarmoon.spark_core.state_machine

import net.minecraft.resources.ResourceLocation

interface IStateMachineHolder {

    val stateMachineHandlers: MutableMap<ResourceLocation, StateMachineHandler>

    fun getStateMachineHandler(id: ResourceLocation): StateMachineHandler? = stateMachineHandlers[id]

}