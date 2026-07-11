package cn.solarmoon.spark_core.animation.vanilla

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.KeyAnimData
import cn.solarmoon.spark_core.animation.model.BonePose
import cn.solarmoon.spark_core.util.toRadians
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

/**
 * 原版实体骨骼修正，替代 BoneUpdateEvent 订阅（省去每帧数千次 EventBus 开销）。
 * 由 [cn.solarmoon.spark_core.animation.anim.AnimController.tick] 直调，
 * 仅对 LivingEntity 生效，其余实体直接跳过。
 */
object BoneModifier {

    /**
     * 对单个骨骼应用原版实体修正（头部朝向 / 睡觉姿态）。
     * 非 LivingEntity 或无关骨骼名则直接返回。
     */
    fun applyBoneTransform(bonePose: BonePose, animatable: IAnimatable<*>) {
        if (animatable !is LivingEntity) return
        when (bonePose.name) {
            "head" -> applyHead(bonePose, animatable)
            "root" -> applySleep(bonePose, animatable)
        }
    }

    private fun applyHead(bonePose: BonePose, player: LivingEntity) {
        val old = bonePose.oLocalTransform
        bonePose.localTransform = KeyAnimData(
            old.position,
            bonePose.localTransform.rotation.add(
                Vec3(-player.xRot.toDouble(), (-player.yHeadRot + player.yBodyRot).toDouble(), 0.0).toRadians()
            ),
            old.scale
        )
    }

    private fun applySleep(bonePose: BonePose, player: LivingEntity) {
        val bedDirection = player.bedOrientation ?: return
        if (!player.isSleeping) return
        val old = bonePose.localTransform

        val bedRotation = sleepDirectionToRotation(bedDirection).toRadians()
        val f3 = player.getEyeHeight(Pose.STANDING) - 1.2
        val offset = Vec3(f3 * bedDirection.stepX, 0.0, f3 * bedDirection.stepZ).yRot(player.yBodyRot.toRadians())

        bonePose.localTransform = KeyAnimData(
            Vec3(old.position.x + offset.x, old.position.y, old.position.z + offset.z),
            Vec3(
                old.rotation.x,
                old.rotation.y + player.yBodyRot.toRadians().toDouble() + PI + bedRotation,
                old.rotation.z
            ),
            old.scale
        )
    }

    private fun sleepDirectionToRotation(facing: Direction) = when (facing) {
        Direction.SOUTH -> 0.0
        Direction.WEST -> 270.0
        Direction.NORTH -> 180.0
        Direction.EAST -> 90.0
        else -> 0.0
    }

}