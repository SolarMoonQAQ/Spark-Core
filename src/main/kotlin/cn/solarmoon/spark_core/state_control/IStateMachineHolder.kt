package cn.solarmoon.spark_core.state_control

import net.minecraft.resources.ResourceLocation

interface IStateMachineHolder<T> {

    val allStateMachines: LinkedHashMap<ResourceLocation, StateMachine<T>>

}