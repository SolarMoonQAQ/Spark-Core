package cn.solarmoon.spark_core.compat.player_animator

import dev.kosmx.playerAnim.impl.IAnimatedPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.fml.ModList

object PlayerAnimatorCompat {
    const val MOD_ID = "playeranimator"
    var IS_LOADED = false
    fun init() {
        IS_LOADED = ModList.get().isLoaded(MOD_ID)
    }

    fun isAnimActive(player: Entity) = IS_LOADED && (player as? IAnimatedPlayer)?.playerAnimator_getAnimation()?.isActive() == true

}