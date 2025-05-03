package cn.solarmoon.spark_core.ik.component

import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.util.IKCoordinateTransformer
import cn.solarmoon.spark_core.physics.level.PhysicsWorld
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.math.Vector3f
import net.minecraft.world.phys.Vec3

/**
 * Represents an active instance of an IK setup on an IEntityAnimatable.
 * Holds the runtime state (the FabrikChain3D) and handles ground contact logic.
 *
 * @property type The configuration type for this IK component.
 * @property chain The runtime FabrikChain3D instance used for solving.
 */
class IKComponent(
    val type: IKComponentType, // Assuming IKComponentType exists in the same or imported package
    val chain: FabrikChain3D, // The runtime IK chain instance
    val host: IEntityAnimatable<*> // The host entity that owns this component
) {
    val chainName: String get() = type.chainName

    // 末端执行器骨骼名称
    val endBoneName: String get() = type.endBoneName

    // --- Terrain Interaction Properties ---
    /** If true, the component will attempt to stick to the ground using raycasting. */
    var stickToGround: Boolean = false

    /** Vertical offset above the desired target to start the ground check raycast. */
    var groundCheckOffset: Vector3f = Vector3f(0f, 0.3f, 0f)

    /** Maximum distance to cast the ray downwards to find the ground. */
    var groundCheckDistance: Float = 1.0f

    /** The desired target position before ground check (usually from animation/logic). */
    var desiredTargetPosition: Vector3f = Vector3f.ZERO.clone()
        private set // Only allow setting via update methods

    /** The actual target position after considering ground contact. This is passed to the IK solver. */
    var actualTargetPosition: Vector3f = Vector3f.ZERO.clone()
        private set // Only allow setting via update methods

    /** True if the ground check raycast found a valid ground surface below the target. */
    var isGrounded: Boolean = false
        private set // Only allow setting via update methods

    /** 执行器到父骨骼的偏移量 */
    var offset: Vector3f = Vector3f.ZERO


    val targetBoneName: String = type.bonePathNames.last()


    init {
        // 初始化时从末端执行器的本地位置读取默认目标点
        initializeDefaultTarget()
    }

    // (Optional) Store hit normal if needed for orientation
    // var groundNormal: Vector3f? = null
    //     private set

    /**
     * 初始化默认目标点，从末端执行器的本地位置读取
     */
    private fun initializeDefaultTarget() {
        try {
            // 获取末端执行器骨骼
            val endBone = host.model.getBone(endBoneName)
            if (endBone != null) {
                // 获取末端执行器在本地空间中的位置（考虑动画变换后的位置）
                val localPos = host.getSpaceBonePivot(endBoneName)

                // 设置为默认目标位置
                this.desiredTargetPosition.set(localPos.x, localPos.y, localPos.z)
                this.actualTargetPosition.set(this.desiredTargetPosition)
            }
        } catch (e: Exception) {
            // 如果获取失败，使用零向量作为默认值
            this.desiredTargetPosition.set(Vector3f.ZERO)
            this.actualTargetPosition.set(Vector3f.ZERO)
        }
    }

    /**
     * 使用世界坐标更新目标位置
     *
     * @param worldPosition 世界坐标中的目标位置
     */
    fun updateTargetWorldPosition(worldPosition: Vector3f) {
        // 将世界坐标转换为本地坐标
        val localPosition = IKCoordinateTransformer.worldToLocalSpaceJME(host, worldPosition)

        // 更新本地坐标目标位置
        updateTargetLocalPosition(localPosition)
    }

    /**
     * 直接使用本地坐标更新目标位置
     *
     * @param localPosition 本地坐标中的目标位置
     */
    fun updateTargetLocalPosition(localPosition: Vector3f) {
        // 存储本地坐标的目标位置
        this.desiredTargetPosition.set(localPosition)

        // 如果不需要地面接触检测，直接使用目标位置
        if (!stickToGround) {
            this.actualTargetPosition.set(localPosition)
            this.isGrounded = false
            return
        }

        // 否则，需要进行地面接触检测
    }

    /**
     * 更新目标位置并进行地面接触检测
     * 这个方法应该在物理步骤和IK解算之前调用，通常在主线程上
     * 注意：该方法期望传入的是世界坐标，并会执行射线检测
     *
     * @param physicsWorld 用于射线检测的物理世界
     * @param worldTargetPosition 世界坐标中的目标位置
     */
    fun updateGroundContact(physicsWorld: PhysicsWorld, worldTargetPosition: Vector3f) {
        // 注意：这里我们不再将世界坐标转换为本地坐标并存储
        // 因为这个方法专门用于射线检测，我们已经在其他地方存储了本地坐标

        if (!stickToGround) {
            // 如果不需要地面接触，则不执行射线检测
            return
        }

        // 使用传入的世界坐标直接进行射线检测
        val rayStart = worldTargetPosition.add(groundCheckOffset)
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
            // 计算世界空间中的命中点
            val worldHitPoint = Vector3f(rayStart).addLocal(scaledDirection)

            // 将世界空间中的命中点转换回本地空间
            val localHitPoint = IKCoordinateTransformer.worldToLocalSpaceJME(host, worldHitPoint)

            // 设置本地空间中的实际目标位置
            this.actualTargetPosition.set(localHitPoint)

            this.isGrounded = true
            // Optional: Store normal for orientation adjustments later
            // this.groundNormal = closestHit.hitNormalLocal.normalizeLocal() // Or hitNormalWorld if available/needed
        } else {
            // No ground found within range: Fallback to the desired position
            // 注意：我们保持当前的actualTargetPosition不变，因为它已经在updateTargetLocalPosition中设置了
            this.isGrounded = false
            // this.groundNormal = null
        }
    }

}