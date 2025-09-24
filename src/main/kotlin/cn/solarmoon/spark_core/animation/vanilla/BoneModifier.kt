package cn.solarmoon.spark_core.animation.vanilla

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.util.toRadians
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import kotlin.math.PI

object BoneModifier {

    @SubscribeEvent
    private fun head(event: BoneUpdateEvent) {
        val player = event.model.animatable
        if (player !is IEntityAnimatable<*> || player !is LivingEntity) return
        val old = event.oldTransform
        if (event.bonePose.name == "head") {
            val netHeadYaw = (player.yHeadRot - player.yBodyRot).coerceIn(-75.0f, 75.0f).toDouble()
            val headPitch = player.xRot.toDouble()
            event.newTransform = KeyAnimData(
                old.position,
                event.originNewTransform.rotation.subtract(Vec3(headPitch, netHeadYaw, 0.0).toRadians()),
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
        val animatable = event.model.animatable
        if (event.bonePose.name == "root" && animatable is LivingEntity) {
            val bedDirection = animatable.bedOrientation
            if (bedDirection != null && animatable.isSleeping) {
                val old = event.originNewTransform

                val bedRotation = sleepDirectionToRotation(bedDirection).toRadians()

                val f3 = animatable.getEyeHeight(Pose.STANDING) - 1.2
                val offset = Vec3(f3 * bedDirection.stepX, 0.0, f3 * bedDirection.stepZ).yRot(animatable.yBodyRot.toRadians())

                event.newTransform = KeyAnimData(
                    Vec3(
                        old.position.x + offset.x,
                        old.position.y,
                        old.position.z + offset.z
                    ),
                    Vec3(
                        old.rotation.x,
                        old.rotation.y + animatable.yBodyRot.toRadians().toDouble() + PI + bedRotation,
                        old.rotation.z
                    ),
                    old.scale
                )
            }
        }
    }

    fun sleepDirectionToRotation(facing: Direction) = when (facing) {
        Direction.SOUTH -> 0.0
        Direction.WEST -> 270.0
        Direction.NORTH -> 180.0
        Direction.EAST -> 90.0
        else -> 0.0
    }

}