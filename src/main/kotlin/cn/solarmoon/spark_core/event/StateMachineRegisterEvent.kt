package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.state_control.StateMachine
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.Event

abstract class StateMachineRegisterEvent<T>(
    private val allStateMachines: LinkedHashMap<ResourceLocation, StateMachine<T>>,
    val holder: T
): Event() {

    fun register(key: ResourceLocation, stateMachine: StateMachine<T>) {
        if (allStateMachines[key] != null) throw Exception("Id为 $key 的状态机已存在于当前对象中，请换一个id。")
        else allStateMachines[key] = stateMachine
    }

    class Entity(
        allStateMachines: LinkedHashMap<ResourceLocation, StateMachine<net.minecraft.world.entity.Entity>>,
        entity: net.minecraft.world.entity.Entity
    ): StateMachineRegisterEvent<net.minecraft.world.entity.Entity>(allStateMachines, entity)

}