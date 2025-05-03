package cn.solarmoon.spark_core.ik.component

import au.edu.federation.caliko.FabrikChain3D
import au.edu.federation.utils.Vec3f
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.ik.caliko.CalikoStructureBuilder
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import net.minecraft.resources.ResourceLocation

/**
 * Represents different types of joint constraints applicable to bones in an IK chain.
 */
sealed interface JointConstraint {
    /** Defines a hinge joint constraint. */
    data class Hinge(
        val rotationAxis: Vec3f,
        val cwLimitDegs: Float,  // Clockwise limit in degrees
        val acwLimitDegs: Float, // Anti-clockwise limit in degrees
        val referenceAxis: Vec3f // Reference axis for hinge rotation limits
    ) : JointConstraint

    /** Defines a ball-and-socket joint constraint. */
    data class BallSocket(
        val angleLimitDegs: Float // Cone angle limit in degrees
    ) : JointConstraint
}
/**
 * Defines the configuration for a type of IK component. Acts as a blueprint.
 * Instances should be registered in a dedicated registry.
 */
data class IKComponentType(
    val id: ResourceLocation,
    val chainName: String,
    val startBoneName: String,
    val endBoneName: String,
    val bonePathNames: List<String>,
    // Optional: Default solver parameters
    val defaultTolerance: Float = 0.1f,
    val defaultMaxIterations: Int = 20,
    val jointConstraints: Map<String, JointConstraint> = emptyMap(), // Map bone name to its constraint
    val ikConstraintId: ResourceLocation? = null, // Optional reference to a loaded IK constraint
    val poleTargetBoneName: String? = null, // Optional: Name of the bone to use as pole target
    val poleAngleDegrees: Float? = null // Optional: Pole angle in degrees
) {

    /**
     * Builds a FabrikChain3D instance based on this type's configuration.
     */
    fun buildChain(owner: IAnimatable<*>, model: OModel): FabrikChain3D? {
        // 首先检查是否有引用到已加载的IK约束
        var constraints = jointConstraints
        var iterations = defaultMaxIterations
        var tolerance = defaultTolerance
        var poleTarget: Vec3f? = null
        var poleAngle: Float? = poleAngleDegrees

        // 如果有指定IK约束ID，则从同步数据中获取
        if (ikConstraintId != null) {
            val ikConstraint = OIKConstraint.get(ikConstraintId)
            if (ikConstraint != OIKConstraint.EMPTY) {
                // 转换骨骼限制为关节约束
                constraints = ikConstraint.ikChainBoneLimits.mapNotNull { (boneName, limit) ->
                    val constraint = limit.toJointConstraint()
                    if (constraint != null) boneName to constraint else null
                }.toMap()

                // 使用约束中的迭代次数（如果有）
                if (ikConstraint.iterations > 0) {
                    iterations = ikConstraint.iterations
                }
            }
        }

        // 创建IK链
        val chain = CalikoStructureBuilder.buildChainFromPath(owner, chainName, bonePathNames, model, constraints)

        // 应用链属性
        chain?.apply {
            setSolveDistanceThreshold(tolerance)
            setMaxIterationAttempts(iterations)
        }

        return chain
    }

}
