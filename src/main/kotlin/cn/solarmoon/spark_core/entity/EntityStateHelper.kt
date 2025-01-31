package cn.solarmoon.spark_core.entity

import cn.solarmoon.spark_core.physics.toRadians
import cn.solarmoon.spark_core.util.Side
import net.minecraft.client.player.LocalPlayer
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun Entity.moveCheck(): Boolean {
    val v = deltaMovement
    val avgV = (abs(v.x) + abs(v.z)) / 2f
    return avgV >= if (isCrouching || (this is LivingEntity && isUsingItem)) 0.0025 else 0.015
}

fun Entity.moveBackCheck(): Boolean {
    val v = deltaMovement
    val forward = Vec3.directionFromRotation(0f, getPreciseBodyRotation(1f))
    // 计算移动的标量与 身体forward 方向的点积，如果乘数大于150度值则代表方向基本相反
    val dotProduct = v.normalize().x * forward.normalize().x + v.normalize().z * forward.normalize().z
    return dotProduct < cos(120f.toRadians()) && moveCheck()
}

fun Entity.isFalling(): Boolean {
    return !onGround() && deltaMovement.y != 0.0 && (this !is Player || !this.abilities.flying)
}

/**
 * 判断输入坐标是否在当前实体朝向的一个扇形角度范围内（输入量都是角度制）
 * @param targetPos 目标实体位置
 * @param rotateY 将扇形区域绕目标中心点进行整体旋转
 */
fun Entity.canSee(targetPos: Vec3, rangeDegrees: Double, rotateY: Float = 0f): Boolean {
    val entityPos = position()

    // 朝向
    val viewVector = Vec3.directionFromRotation(0f, yRot).toVector3f().rotateY(rotateY.toRadians()).toVec3()

    // 计算实体到目标实体的向量
    val directionToTarget = targetPos.subtract(entityPos).normalize()

    // 计算视线向量和方向向量的点积
    val dotProduct = viewVector.dot(directionToTarget)

    // 夹角的余弦值
    val thresholdCosAngle = cos(Math.toRadians(rangeDegrees / 2))

    // 判断夹角是否小于指定度数
    return dotProduct > thresholdCosAngle
}

/**
 * 根据扇形角度划分目标位置在该实体的哪个相对方向上，其中前后为135度扇形，左右为45度扇形
 */
fun Entity.getSide(targetPos: Vec3): Side {
    return when {
        canSee(targetPos, 135.0, 0f) -> Side.FRONT
        canSee(targetPos, 45.0, 90f) -> Side.LEFT
        canSee(targetPos, 135.0, 180f) -> Side.BACK
        canSee(targetPos, 45.0, 270f) -> Side.RIGHT
        else -> Side.FRONT
    }
}

/**
 * 只判断左右侧的[getSide]
 * @param invert 是否反转输出结果
 */
fun Entity.getLateralSide(targetPos: Vec3, invert: Boolean = false): Side {
    return when {
        canSee(targetPos, 180.0, 90f) -> if (!invert) Side.LEFT else Side.RIGHT
        canSee(targetPos, 180.0, 270f) -> if (!invert) Side.RIGHT else Side.LEFT
        else -> Side.RIGHT
    }
}

fun Entity.smoothLookAt(target: Vec3, partialTicks: Float = 1f) {
    val vec3 = EntityAnchorArgument.Anchor.EYES.apply(this)
    val d0 = target.x - vec3.x
    val d1 = target.y - vec3.y
    val d2 = target.z - vec3.z
    val d3 = sqrt(d0 * d0 + d2 * d2)
    this.xRot = Mth.rotLerp(partialTicks, xRotO, Mth.wrapDegrees((-(Mth.atan2(d1, d3) * 180.0F / PI))).toFloat())
    this.yRot = Mth.rotLerp(partialTicks, yRotO, Mth.wrapDegrees((Mth.atan2(d2, d0) * 180.0F / PI) - 90.0F).toFloat())
}

/**
 * 根据玩家客户端输入和玩家自身朝向输出玩家的移动速度向量
 */
fun LocalPlayer.getInputVector(): Vec3 {
    val v = input.moveVector.normalized()
    val f2 = v.x
    val f3 = v.y
    val f4 = sin(yRot * (PI / 180.0))
    val f5 = cos(yRot * (PI / 180.0))
    return Vec3((f2 * f5 - f3 * f4), deltaMovement.y, (f3 * f5 + f2 * f4))
}

/**
 * 获取以生物水平朝向为内容的移动值
 * @param mul 对水平速度进行乘积以调整大小
 */
fun Entity.getForwardMoveVector(mul: Float): Vec3 {
    return Vec3(forward.x * mul, deltaMovement.y, forward.z * mul)
}

/**
 * 根据该生物的攻速以及输入的基础攻速进行差值，以此获取一个基于攻速的动画速度
 */
fun Entity.getAttackAnimSpeed(baseSpeedValue: Float): Float {
    val entity = this
    if (entity is LivingEntity) {
        val sp = entity.getAttribute(Attributes.ATTACK_SPEED) ?: return 1f
        return ((sp.value.toFloat() - baseSpeedValue) / 2 + 1f).coerceAtLeast(0.05f)
    } else return 1f
}

/**
 * 朝着[attacker]目视方向后退
 */
fun LivingEntity.knockBackRelativeView(attacker: Entity, strength: Double) {
    knockback(strength, sin(attacker.yRot * (PI / 180.0)), -cos(attacker.yRot * (PI / 180.0)))
}

/**
 * 朝着与目标位置连线方向击退
 */
fun LivingEntity.knockBackRelative(relative: Vec3, strength: Double) {
    knockback(strength, relative.x - x, relative.z - z)
}
