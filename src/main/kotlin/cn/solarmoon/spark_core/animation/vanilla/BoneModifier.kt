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
    private fun head(event: BoneUpdateEvent) {
        val player = event.animatable
        if (player !is IEntityAnimatable<*> || player !is LivingEntity) return
        val old = event.oldData
        if (event.bone.name == "head") {
            event.newData = KeyAnimData(
                old.position,
                event.newData.rotation.add(Vec3(-player.xRot.toDouble(), (-player.yHeadRot + player.yBodyRot).toDouble(), 0.0).toRadians()),
                old.scale
            )
        }
    }

//    @SubscribeEvent
//    private fun waist(event: BoneUpdateEvent) {
//        val player = event.animatable
//        if (player !is IEntityAnimatable<*> || player !is LivingEntity) return
//        val old = event.oldData
//        if (event.bone.name == "waist") {
//            event.newData = KeyAnimData(
//                old.position,
//                event.newData.rotation.add(Vec3(-player.xRot.toDouble().toRadians() / 4.0, 0.0, 0.0)),
//                old.scale
//            )
//        }
//    }

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