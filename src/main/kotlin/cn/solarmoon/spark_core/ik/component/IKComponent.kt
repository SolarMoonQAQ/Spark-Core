package cn.solarmoon.spark_core.ik.component

import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.physics.level.PhysicsWorld
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.math.Vector3f
import net.minecraft.world.phys.Vec3

/**
 * Represents an active instance of an IK setup on an IKHost.
 * Holds the runtime state (the FabrikChain3D) and handles ground contact logic.
 *
 * @property type The configuration type for this IK component.
 * @property chain The runtime FabrikChain3D instance used for solving.
 */
class IKComponent(
    val type: IKComponentType, // Assuming IKComponentType exists in the same or imported package
    val chain: FabrikChain3D // The runtime IK chain instance
) {
    val chainName: String get() = type.chainName

    // --- Terrain Interaction Properties ---
    /** If true, the component will attempt to stick to the ground using raycasting. */
    var stickToGround: Boolean = false

    /** Vertical offset above the desired target to start the ground check raycast. */
    var groundCheckOffset: Vector3f = Vector3f(0f, 0.3f, 0f)

    /** Maximum distance to cast the ray downwards to find the ground. */
    var groundCheckDistance: Float = 1.0f

    /** The desired target position before ground check (usually from animation/logic). */
    var desiredTargetPosition: Vector3f = Vector3f.ZERO.clone()
        private set // Only allow setting via updateGroundContact

    /** The actual target position after considering ground contact. This is passed to the IK solver. */
    var actualTargetPosition: Vector3f = Vector3f.ZERO.clone()
        private set // Only allow setting via updateGroundContact

    /** True if the ground check raycast found a valid ground surface below the target. */
    var isGrounded: Boolean = false
        private set // Only allow setting via updateGroundContact

    // (Optional) Store hit normal if needed for orientation
    // var groundNormal: Vector3f? = null
    //     private set

    /**
     * Updates the target position based on ground contact detection using raycasting.
     * This should be called BEFORE the physics step and IK solving, likely on the main thread.
     *
     * @param physicsWorld The physics world to perform the raycast in.
     * @param newDesiredTargetPosition The latest desired target position (e.g., from animation) in world space, using JME's Vector3f.
     */
    fun updateGroundContact(physicsWorld: PhysicsWorld, newDesiredTargetPosition: Vector3f) {
        this.desiredTargetPosition.set(newDesiredTargetPosition) // Store the desired target

        if (!stickToGround) {
            this.actualTargetPosition.set(newDesiredTargetPosition)
            this.isGrounded = false
            // this.groundNormal = null
            return
        }

        val rayStart = newDesiredTargetPosition.add(groundCheckOffset)
        // Ray points downwards along the world Y-axis from the starting point
        val rayEnd = rayStart.subtract(0f, groundCheckDistance, 0f)

        val results = ArrayList<PhysicsRayTestResult>()
        // Use the available rayTest method. Filtering will happen in the loop below.
        physicsWorld.rayTest(rayStart, rayEnd, results)

        var closestHit: PhysicsRayTestResult? = null
        var minHitFraction = Float.MAX_VALUE

        for (result in results) {
            // --- Raycast Filtering ---
            // Adjust this filter based on your physics setup for terrain.
            // Example: Only collide with static objects belonging to collision group 1 (assuming this is terrain).
            val collisionObject = result.collisionObject
            if (collisionObject != null && collisionObject.isStatic && collisionObject.collisionGroup == 1) {
                // Find the closest valid hit along the ray path
                if (result.hitFraction < minHitFraction) {
                    minHitFraction = result.hitFraction
                    closestHit = result
                }
            }
        }

        if (closestHit != null) {
            // Found ground: Calculate hit point manually and set as actual target
            // hitPoint = rayStart + (rayEnd - rayStart) * closestHit.hitFraction



            val direction = rayEnd.subtract(rayStart) // Calculate direction vector (end - start)
            val scaledDirection = direction.multLocal(closestHit.hitFraction) // Scale direction by hit fraction (modifies 'direction')
            this.actualTargetPosition.set(rayStart).addLocal(scaledDirection) // Set to start point and add scaled direction

            this.isGrounded = true
            // Optional: Store normal for orientation adjustments later
            // this.groundNormal = closestHit.hitNormalLocal.normalizeLocal() // Or hitNormalWorld if available/needed
        } else {
            // No ground found within range: Fallback to the desired position
            this.actualTargetPosition.set(newDesiredTargetPosition)
            this.isGrounded = false
            // this.groundNormal = null
        }
    }

}