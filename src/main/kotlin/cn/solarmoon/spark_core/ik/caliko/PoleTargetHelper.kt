package cn.solarmoon.spark_core.ik.caliko

import au.edu.federation.caliko.FabrikChain3D
import au.edu.federation.utils.Vec3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * 提供对Caliko IK系统的极向目标(Pole Target)支持的辅助类。
 *
 * 极向目标是一种用于控制IK链弯曲方向的技术，常用于膝盖和肘部等关节。
 * 由于Caliko库本身不直接支持极向目标，这个辅助类提供了相关功能。
 */
object PoleTargetHelper {

    /**
     * 应用极向目标到IK链。
     *
     * 这个方法在IK求解前调用，用于调整链的初始状态，使其朝向极向目标的方向弯曲。
     *
     * @param chain 要应用极向目标的IK链
     * @param poleTarget 极向目标的世界坐标位置
     * @param poleAngleDegrees 极向角度（以度为单位），用于旋转极向方向
     */
    fun applyPoleTarget(chain: FabrikChain3D, poleTarget: Vec3f, poleAngleDegrees: Float) {
        // 确保链至少有3个骨骼（基础骨骼、中间骨骼和末端骨骼）
        if (chain.numBones < 2) {
            return // 链太短，无法应用极向目标
        }

        // 获取链的起点和终点
        val startPos = chain.baseLocation
        val endPos = chain.getEffectorLocation()

        // 计算从起点到终点的方向向量
        val chainDirection = Vec3f(endPos).minus(startPos).normalised()

        // 计算从起点到极向目标的方向向量
        val poleDirection = Vec3f(poleTarget).minus(startPos).normalised()

        // 计算垂直于链方向的平面上的极向投影
        // 首先，从极向方向中减去沿链方向的分量
        val dotProduct = poleDirection.dot(chainDirection)
        val projectionOnChainDir = Vec3f(chainDirection).times(dotProduct)
        val poleProjection = Vec3f(poleDirection).minus(projectionOnChainDir).normalised()

        // 应用极向角度旋转
        val poleAngleRadians = Math.toRadians(poleAngleDegrees.toDouble())
        val rotatedPole = rotateAroundAxis(poleProjection, chainDirection, poleAngleRadians.toFloat())

        // 现在我们有了一个指向"弯曲方向"的向量
        // 我们可以使用这个向量来调整链中间骨骼的位置

        // 对于链中的每个中间骨骼（跳过第一个和最后一个）
        for (i in 1 until chain.numBones - 1) {
            val bone = chain.getBone(i)

            // 获取骨骼的当前起点和终点
            val boneStart = bone.startLocation
            val boneEnd = bone.endLocation

            // 计算骨骼长度
            val boneLength = bone.length()

            // 计算骨骼中点
            val boneDirection = Vec3f(boneEnd).minus(boneStart)
            val boneMidpoint = Vec3f(boneStart).plus(boneDirection.times(0.5f))

            // 计算从链起点到骨骼中点的向量
            val startToBoneMid = Vec3f(boneMidpoint).minus(startPos)

            // 计算骨骼中点到链方向的距离
            val distanceAlongChain = startToBoneMid.dot(chainDirection)

            // 计算骨骼中点在链方向上的投影点
            val projectionOnChain = Vec3f(startPos).plus(Vec3f(chainDirection).times(distanceAlongChain))

            // 计算从投影点到骨骼中点的向量
            val projectionToBoneMid = Vec3f(boneMidpoint).minus(projectionOnChain)

            // 计算骨骼应该弯曲的程度（基于链长度的比例）
            val bendFactor = 0.5f * boneLength

            // 计算新的骨骼中点位置，向极向方向偏移
            val newBoneMidpoint = Vec3f(projectionOnChain).plus(Vec3f(rotatedPole).times(bendFactor))

            // 计算从当前中点到新中点的偏移
            val offset = Vec3f(newBoneMidpoint).minus(boneMidpoint)

            // 应用偏移到骨骼的起点和终点
            bone.startLocation = Vec3f(boneStart).plus(offset)
            bone.endLocation = Vec3f(boneEnd).plus(offset)
        }
    }

    /**
     * 围绕指定轴旋转向量。
     *
     * @param vector 要旋转的向量
     * @param axis 旋转轴（应该是单位向量）
     * @param angleRadians 旋转角度（弧度）
     * @return 旋转后的向量
     */
    private fun rotateAroundAxis(vector: Vec3f, axis: Vec3f, angleRadians: Float): Vec3f {
        val cosAngle = cos(angleRadians)
        val sinAngle = sin(angleRadians)

        // 使用罗德里格旋转公式 (Rodrigues' rotation formula)
        val term1 = Vec3f(vector).times(cosAngle)
        val term2 = Vec3f(axis).cross(vector).times(sinAngle)
        val term3 = Vec3f(axis).times(axis.dot(vector) * (1 - cosAngle))

        return Vec3f(term1).plus(term2).plus(term3)
    }
}
