package cn.solarmoon.spark_core.event

import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.event.entity.player.PlayerEvent

/**
 * 注入在[Player.getAttackStrengthScale]，这个方法是原版用于根据武器cd衰减伤害的
 */
class PlayerGetAttackStrengthEvent(player: Player, val adjustTicks: Float, var attackStrengthScale: Float): PlayerEvent(player) {



}