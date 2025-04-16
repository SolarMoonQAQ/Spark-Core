package cn.solarmoon.spark_core.ik.caliko

import au.edu.federation.caliko.BoneConnectionPoint
import au.edu.federation.caliko.FabrikBone3D
import au.edu.federation.caliko.FabrikChain3D
import au.edu.federation.caliko.FabrikJoint3D // Import needed for joint manipulation
import au.edu.federation.caliko.FabrikStructure3D
import au.edu.federation.utils.Vec3f
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.ik.component.JointConstraint // Import the constraint definition
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.ik.caliko.CalikoUtils.toCalikoVec3f
import cn.solarmoon.spark_core.ik.component.IKHost
import cn.solarmoon.spark_core.physics.div
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Builds Caliko IK structures (Chains and Structures) from Spark Core's OModel definition.
 */
object CalikoStructureBuilder {

    private const val ZERO_LENGTH_THRESHOLD = 1e-5f

    fun getBoneWorldPivots(
        owner: IAnimatable<*>,
        bonePathNames: List<String>,
        partialTick: Float = 1f
    ):  LinkedHashMap<String, Vector3f> {
        var worldPivots =  LinkedHashMap<String, Vector3f>()
        val worldMatrix = Matrix4f()
        val worldPos = owner.getWorldPosition(partialTick)
        worldMatrix.translate(worldPos.toVector3f())
        worldMatrix.rotateY(owner.getRootYRot(partialTick))
        val boneGroup = owner.model.bones.filter { it.key in bonePathNames }.values
        boneGroup.forEach {
            it.applyTransformWithParents(owner.bones, worldMatrix, partialTick)
            var pivot = it.pivot.toVector3f()
            worldMatrix.transformPosition(pivot)
            worldPivots[it.name] = pivot
        }
        return worldPivots
    }

    /**
     * Builds a single IK chain based on bone names within a model.
     *
     * @param chainName A name for the created chain.
     * @param startBoneName The name of the first bone in the IK chain (the base bone).
     * @param endBoneName The name of the last bone (effector) in the IK chain.
     * @param model The OModel containing the bone definitions.
     * @param jointConstraints A map linking bone names (specifically, the bone *starting* a segment) to their desired joint constraints.
     * @return A FabrikChain3D representing the IK chain, or null if bones are not found or hierarchy is invalid.
     */
    fun buildChain(
        owner: IAnimatable<*>, chainName: String, startBoneName: String, endBoneName: String, model: OModel,
        jointConstraints: Map<String, JointConstraint> = emptyMap() // Add constraints parameter with default
    ): FabrikChain3D? {

        val startBone = model.getBone(startBoneName) ?: run {
            println("Error: Start bone '$startBoneName' not found in model.")
            return null
        }
        val endBone = model.getBone(endBoneName) ?: run {
            println("Error: End bone '$endBoneName' not found in model.")
            return null
        }

        // 1. Trace hierarchy from endBone up to startBone
        val bonePath = mutableListOf<OBone>()
        var current: OBone? = endBone
        var foundStartBone = false
        while (current != null) {
            bonePath.add(current)
            if (current.name == startBoneName) {
                foundStartBone = true
                break
            }
            current = current.parentName?.let { model.getBone(it) }
        }

        // Error handling: Check if startBone was found in the hierarchy
        if (!foundStartBone) {
             println("Error: Start bone '$startBoneName' is not an ancestor of end bone '$endBoneName'.")
             return null
        }
        bonePath.reverse() // Order from startBone to endBone

        // Handle the case where start and end bones are the same
        if (startBone == endBone) {
            println("Info: Chain '$chainName' has start and end bone the same ('${startBone.name}'). Creating chain with 0 bones.")
            val chain = FabrikChain3D(chainName)
            val baseLocationWorld = startBone.pivot.toVector3f().mulPosition(owner.getWorldBoneMatrix(startBone.name))
            chain.baseLocation = baseLocationWorld.toCalikoVec3f()
            // A chain with 0 bones is valid in Caliko, representing just the base location.
            return chain
        }

        // 2. Create the Caliko chain
        val chain = FabrikChain3D(chainName)

        // 3. Calculate world positions and build FabrikBone3D instances
        // The first bone in the path (startBone) defines the base location and the start of the first FabrikBone
        val baseLocationWorld = startBone.pivot.toVector3f().mulPosition(owner.getWorldBoneMatrix(startBone.name))
        chain.baseLocation = baseLocationWorld.toCalikoVec3f()

        var currentBoneStartPosWorld = baseLocationWorld

        // Iterate through the path, creating a FabrikBone for each segment *between* pivots
        // The number of FabrikBones is one less than the number of OBones in the path.
        for (i in 0 until bonePath.size - 1) {
            val currentOBone = bonePath[i] // Bone defining the start of the segment
            val nextOBone = bonePath[i+1] // Bone defining the end of the segment

            // The end position of the current FabrikBone is the pivot of the *next* OBone
            val currentBoneEndPosWorld = nextOBone.pivot.toVector3f().mulPosition(owner.getWorldBoneMatrix(nextOBone.name))

            // Calculate bone length and direction
            val boneVector = Vector3f()
            currentBoneEndPosWorld.sub(currentBoneStartPosWorld, boneVector) // Vector from start to end
            val boneLength = boneVector.length()

            // Ensure bone length is not zero or negligible to avoid issues
            if (abs(boneLength) < ZERO_LENGTH_THRESHOLD) {
                 println("Warning: Bone segment between '${currentOBone.name}' and '${nextOBone.name}' has near-zero length ($boneLength). Skipping.")
                 // Update the start position for the *next* potential segment, effectively collapsing this one.
                 currentBoneStartPosWorld = currentBoneEndPosWorld
                 continue // Skip adding this zero-length bone
            }

            // Create the FabrikBone3D.
            // Caliko's FabrikBone3D constructor takes start and *direction* vector, not start and end points.
            // The direction should be normalized.
            val boneDirection = boneVector.normalize() // Normalize the vector for direction

            // The 'name' here logically corresponds to the OBone *starting* this segment.
            val fabrikBone = FabrikBone3D(
                currentBoneStartPosWorld.toCalikoVec3f(),
                boneDirection.toCalikoVec3f(), // Use normalized direction
                boneLength, // Provide the calculated length
                currentOBone.name // Name the FabrikBone after the starting OBone of the segment
            )

            // --- Apply Joint Constraints ---
            // Constraints are defined based on the bone *starting* the segment (currentOBone).
            jointConstraints[currentOBone.name]?.let { constraint ->
                val joint = fabrikBone.joint // Get the joint associated with this bone
                when (constraint) {
                    is JointConstraint.Hinge -> {
                        // Use LOCAL_HINGE as per previous assumption in IKComponentType
                        joint.setAsLocalHinge(constraint.rotationAxis, constraint.cwLimitDegs, constraint.acwLimitDegs, constraint.referenceAxis)
                    }
                    is JointConstraint.BallSocket -> {
                        joint.setAsBallJoint(constraint.angleLimitDegs)
                    }
                }
            }

            chain.addBone(fabrikBone)

            // Update the start position for the next bone segment
            currentBoneStartPosWorld = currentBoneEndPosWorld
        }

        // If the chain is empty after processing (e.g., all segments were zero length)
        if (chain.numBones == 0) {
             println("Warning: Chain '$chainName' created with zero bones despite start != end. Check bone hierarchy and lengths.")
             // Depending on requirements, might return null or an empty chain.
             // Returning the chain for now, but it won't solve.
        }

        // Optional: Set base bone constraint type if needed (e.g., fixed, local hinge, global hinge)
        // chain.setBaseboneConstraintType(BaseboneConstraintType3D.GLOBAL_ABSOLUTE); // Example: Fix the base in world space
        // chain.setFixedBaseMode(true); // Often used with GLOBAL_ABSOLUTE

        // Optional: Set chain properties like tolerance, max iterations
        // chain.setSolveDistanceThreshold(0.1f)
        // chain.setMaxIterationAttempts(20)

        return chain
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
                println("Error: Bone '$boneName' in path for chain '$chainName' not found in model.")
                return null // Return null if any bone is missing
            }
        }

        // 2. Create the Caliko chain
        val chain = FabrikChain3D(chainName)
        val worldPivots = getBoneWorldPivots(owner, bonePathNames)
        // 3. Set base location from the first bone in the path
        val baseLocationWorld = worldPivots[bonePath.first().name] ?: run {
            SparkCore.LOGGER.error("Error: Bone '${bonePath.first().name}' in path for chain '$chainName' not found in model.")
            return null
        }
        chain.baseLocation = baseLocationWorld.toCalikoVec3f()
        var currentBoneStartPosWorld = baseLocationWorld

        // 4. Iterate through the path segments to create FabrikBone3D instances
        for (i in 0 until bonePath.size -1) {
            val currentOBone = bonePath[i] // Bone defining the start pivot of the segment
            val nextOBone = bonePath[i + 1] // Bone defining the end pivot of the segment

            val currentBoneEndPosWorld = worldPivots[nextOBone.name] ?: run {
                println("Error: Bone '${nextOBone.name}' in path for chain '$chainName' not found in model.")
                return null // Return null if any bone is missing
            }

            val boneVector = Vector3f()
            currentBoneEndPosWorld.sub(currentBoneStartPosWorld, boneVector)
            val boneLength = boneVector.length()

            if (abs(boneLength) < ZERO_LENGTH_THRESHOLD) {
                println("Warning: Bone segment between '${currentOBone.name}' and '${nextOBone.name}' in chain '$chainName' has near-zero length ($boneLength). Skipping.")
                currentBoneStartPosWorld = currentBoneEndPosWorld // Collapse segment
                continue
            }

            val boneDirection = boneVector.normalize()
            val fabrikBone = FabrikBone3D(
                currentBoneStartPosWorld.toCalikoVec3f(),
                boneDirection.toCalikoVec3f(),
                boneLength,
                currentOBone.name // Name FabrikBone after the starting OBone of the segment
            )

            // Apply constraints based on the starting bone of the segment
            jointConstraints[currentOBone.name]?.let { constraint ->
                applyConstraintToJoint(fabrikBone.joint, constraint) // Use helper function
            }

            chain.addBone(fabrikBone)
            currentBoneStartPosWorld = currentBoneEndPosWorld
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