package cn.solarmoon.spark_core.animation.preset_anim

import net.minecraft.client.player.LocalPlayer

fun LocalPlayer.getStateMachine() = (this as IPlayerStateAnimMachineHolder).stateMachine