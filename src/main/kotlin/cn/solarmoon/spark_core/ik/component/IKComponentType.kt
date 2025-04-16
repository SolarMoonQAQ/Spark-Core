package cn.solarmoon.spark_core.ik.component

import au.edu.federation.caliko.FabrikChain3D
import au.edu.federation.utils.Vec3f
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.ik.caliko.CalikoStructureBuilder
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
    val id: ResourceLocation, // Unique identifier for registration
    val chainName: String,    // Name used to identify the chain instance and target position
    val startBoneName: String,
    val endBoneName: String,
    val bonePathNames: List<String>? = null, // Optional: Explicit path of bone names for the chain
    // Optional: Default solver parameters
    val defaultTolerance: Float = 0.1f,
    val defaultMaxIterations: Int = 20,
    val jointConstraints: Map<String, JointConstraint> = emptyMap() // Map bone name to its constraint
) {

    /**
     * Builds a FabrikChain3D instance based on this type's configuration.
     */
    fun buildChain(owner: IAnimatable<*>, model: OModel): FabrikChain3D? {
        val chain = if (!bonePathNames.isNullOrEmpty()) {
            // Use the explicit path if provided
            println("Building chain '$chainName' using explicit path: $bonePathNames")
            CalikoStructureBuilder.buildChainFromPath(owner, chainName, bonePathNames, model, jointConstraints)
        } else {
            // Fallback to using start and end bone names
            CalikoStructureBuilder.buildChain(owner, chainName, startBoneName, endBoneName, model, jointConstraints)
        }
        chain?.apply {
            setSolveDistanceThreshold(defaultTolerance)
            setMaxIterationAttempts(defaultMaxIterations)
            // Constraints are now applied during the build process in CalikoStructureBuilder
        }
        return chain
    }

}
