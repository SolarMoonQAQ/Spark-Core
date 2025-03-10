package cn.solarmoon.spark_core.compat.player_animator

import dev.kosmx.playerAnim.impl.IAnimatedPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.ModList

object PlayerAnimatorHelper {

    fun isLoaded() = ModList.get().isLoaded("playeranimator")

    fun isAnimActive(player: Entity) = (player as? IAnimatedPlayer)?.playerAnimator_getAnimation()?.isActive() == true

}