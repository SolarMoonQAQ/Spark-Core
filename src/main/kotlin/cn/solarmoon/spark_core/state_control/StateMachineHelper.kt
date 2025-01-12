package cn.solarmoon.spark_core.state_control

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity


@Suppress("unchecked_cast")
fun Entity.getAllStateMachines() = (this as IStateMachineHolder<Entity>).allStateMachines

fun Entity.getStateMachine(id: ResourceLocation) = getAllStateMachines()[id] ?: throw Exception("找不到id为 $id 的状态机")