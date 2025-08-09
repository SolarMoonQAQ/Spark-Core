package cn.solarmoon.spark_core.event

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.event.entity.player.PlayerEvent

class PlayerFallEvent(
    player: Player,
    val distance: Float,
    val multiplier: Float,
    val damageSource: DamageSource
): PlayerEvent(player) {
}