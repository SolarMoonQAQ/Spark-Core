package cn.solarmoon.spark_core.animation.vanilla

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.phys.toRadians
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

object PlayerBoneModifier {

    @SubscribeEvent
    private fun playerTick(event: PlayerTickEvent.Pre) {
        val player = event.entity
        if (player !is IEntityAnimatable<*>) return
        val head = player.getBone("head")
        head.updateVanilla(KeyAnimData(
            Vec3.ZERO,
            Vec3(-player.xRot / 1.5, (-player.yHeadRot + player.yBodyRot).toDouble(), 0.0).toRadians(),
            Vec3.ZERO
        ))

        val waist = player.getBone("waist")
        waist.updateVanilla(KeyAnimData(
            Vec3.ZERO,
            Vec3(-player.xRot.toDouble().toRadians() / 3, 0.0, 0.0),
            Vec3.ZERO,
        ))
    }

}