package cn.solarmoon.spark_core.entity.player

import net.minecraft.client.player.Input

interface ILocalPlayerPatch: IPlayerPatch {

    var savedInput: Input

}