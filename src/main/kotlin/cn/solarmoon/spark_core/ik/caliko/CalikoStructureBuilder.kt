package cn.solarmoon.spark_core.ik.caliko

import au.edu.federation.caliko.FabrikBone3D
import au.edu.federation.caliko.FabrikChain3D
import au.edu.federation.caliko.FabrikJoint3D
import au.edu.federation.utils.Vec3f
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.ik.util.CalikoUtils.toCalikoVec3f
import cn.solarmoon.spark_core.ik.component.JointConstraint
import org.joml.Vector3f
import kotlin.math.abs


object CalikoStructureBuilder {

    private const val ZERO_LENGTH_THRESHOLD = 1e-5f

    /**
     * 应用极向目标到IK链。
     *
     * @param chain 要应用极向目标的IK链
     * @param poleTarget 极向目标的世界坐标位置
     * @param poleAngleDegrees 极向角度（以度为单位），用于旋转极向方向
     */
    fun applyPoleTarget(chain: FabrikChain3D, poleTarget: Vec3f, poleAngleDegrees: Float) {
        PoleTargetHelper.applyPoleTarget(chain, poleTarget, poleAngleDegrees)
    }

    fun getBonePivots(
        owner: IAnimatable<*>,
        bonePathNames: List<String>,
    ):  LinkedHashMap<String, Vector3f> {
        val localPivots =  LinkedHashMap<String, Vector3f>()
        val boneGroup = owner.modelController.originModel.bones.filter { it.key in bonePathNames }.values
        boneGroup.forEach {
            localPivots[it.name] = owner.modelController.model?.getBonePose(it.name)?.getSpaceBonePivot() ?: Vector3f()
        }
        return localPivots
    }

    /**
     * Builds a single IK chain from an explicit path of bone names.
     * This allows defining chains that might not follow a direct parent-child hierarchy lookup
     * between just a start and end bone, or for more explicit control over the segments.
     *
     * @param chainName A name for the created chain.
     * @param bonePathNames A list of bone names defining the key points (pivots) along the chain, in order.
     *                      The first name defines the base location. Segments are created between consecutive names.
     * @param model The OModel containing the bone definitions.
     * @param jointConstraints A map linking bone names (specifically, the bone *starting* a segment) to their desired joint constraints.
     * @return A FabrikChain3D representing the IK chain, or null if any bone is not found or the path is too short.
     */
    fun buildChainFromPath(
        owner: IAnimatable<*>,
        chainName: String,
        bonePathNames: List<String>,
        model: OModel,
        jointConstraints: Map<String, JointConstraint> = emptyMap()
    ): FabrikChain3D? {
        if (bonePathNames.size < 2) {
            println("Error: Bone path for chain '$chainName' must contain at least two bone names (start and end pivot).")
            return null
        }

        // 1. Validate all bones exist and get OBone instances
        val bonePath = bonePathNames.map { boneName ->
            model.getBone(boneName) ?: run {
                SparkCore.LOGGER.error("Error: Bone '$boneName' in path for chain '$chainName' not found in model.")
                return null // Return null if any bone is missing
            }
        }

        // 2. Create the Caliko chain
        val chain = FabrikChain3D(chainName)
        val localPivots = getBonePivots(owner, bonePathNames)
        // 3. Set base location from the first bone in the path
        val baseLocationLocal = localPivots[bonePath.first().name] ?: run {
            SparkCore.LOGGER.error("Error: Bone '${bonePath.first().name}' in path for chain '$chainName' not found in model.")
            return null
        }
        chain.baseLocation = baseLocationLocal.toCalikoVec3f()
        var currentBoneStartPosLocal = baseLocationLocal

        // 4. Iterate through the path segments to create FabrikBone3D instances
        for (i in 0 until bonePath.size -1) {
            val currentOBone = bonePath[i] // Bone defining the start pivot of the segment
            val nextOBone = bonePath[i + 1] // Bone defining the end pivot of the segment

            val currentBoneEndPosLocal = localPivots[nextOBone.name] ?: run {
                println("Error: Bone '${nextOBone.name}' in path for chain '$chainName' not found in model.")
                return null // Return null if any bone is missing
            }

            val boneVector = Vector3f()
            currentBoneEndPosLocal.sub(currentBoneStartPosLocal, boneVector)
            val boneLength = boneVector.length()

            if (abs(boneLength) < ZERO_LENGTH_THRESHOLD) {
                println("Warning: Bone segment between '${currentOBone.name}' and '${nextOBone.name}' in chain '$chainName' has near-zero length ($boneLength). Skipping.")
                currentBoneStartPosLocal = currentBoneEndPosLocal // Collapse segment
                continue
            }

            val boneDirection = boneVector.normalize()
            val fabrikBone = FabrikBone3D(
                currentBoneStartPosLocal.toCalikoVec3f(),
                boneDirection.toCalikoVec3f(),
                boneLength,
                currentOBone.name // Name FabrikBone after the starting OBone of the segment
            )

            // Apply constraints based on the starting bone of the segment
            jointConstraints[currentOBone.name]?.let { constraint ->
                applyConstraintToJoint(fabrikBone.joint, constraint) // Use helper function
            }

            chain.addBone(fabrikBone)
            currentBoneStartPosLocal = currentBoneEndPosLocal
        }

        return chain
    }

    // Helper function to apply constraint to a joint (extracted logic)
    private fun applyConstraintToJoint(joint: FabrikJoint3D, constraint: JointConstraint) {
        when (constraint) {
            is JointConstraint.Hinge -> {
                joint.setAsLocalHinge(constraint.rotationAxis, constraint.cwLimitDegs, constraint.acwLimitDegs, constraint.referenceAxis)
            }
            is JointConstraint.BallSocket -> {
                joint.setAsBallJoint(constraint.angleLimitDegs)
            }
        }
    }
}