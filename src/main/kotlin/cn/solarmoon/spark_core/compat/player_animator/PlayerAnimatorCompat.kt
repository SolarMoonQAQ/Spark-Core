package cn.solarmoon.spark_core.compat.player_animator

import dev.kosmx.playerAnim.impl.IAnimatedPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.fml.ModList

object PlayerAnimatorCompat {

    fun isLoaded() = ModList.get().isLoaded("playeranimator")

    fun isAnimActive(player: Entity) = isLoaded() && (player as? IAnimatedPlayer)?.playerAnimator_getAnimation()?.isActive() == true

}