package cn.solarmoon.spark_core.animation.vanilla

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.physics.toRadians
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object BoneModifier {

    @SubscribeEvent
    private fun playerTick(event: EntityTickEvent.Pre) {
        val player = event.entity
        if (player !is IEntityAnimatable<*> || player !is LivingEntity) return
        val head = player.getBone("head")
        head.updateVanilla(KeyAnimData(
            Vec3.ZERO,
            Vec3(-player.xRot / 1.5, (-player.yHeadRot + player.yBodyRot).toDouble(), 0.0).toRadians(),
            Vec3.ZERO
        ))

        val waist = player.getBone("waist")
        waist.updateVanilla(KeyAnimData(
            Vec3.ZERO,
            Vec3(-player.xRot.toDouble().toRadians() / 4.0, 0.0, 0.0),
            Vec3.ZERO
        ))
    }

    @SubscribeEvent
    private fun sleep(event: BoneUpdateEvent) {
        val player = event.animatable
        if (event.bone.name == "body" && player is Player) {

            val bedDirection = player.bedOrientation
            if (bedDirection != null && player.isSleeping) {
                val old = event.newData
                event.newData = KeyAnimData(
                    old.position,
                    Vec3(old.rotation.x, old.rotation.y, old.rotation.z),
                    old.scale
                )
            }

        }
    }

}