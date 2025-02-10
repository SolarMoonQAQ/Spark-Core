package cn.solarmoon.spark_core.entity.player

import net.minecraft.client.player.Input
import ru.nsk.kstatemachine.statemachine.StateMachine

interface ILocalPlayerPatch {

    var savedInput: Input

    val stateMachine: StateMachine

}