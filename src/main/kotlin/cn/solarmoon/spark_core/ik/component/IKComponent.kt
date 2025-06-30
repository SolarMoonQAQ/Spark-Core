package cn.solarmoon.spark_core.ik.component

import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.util.IKCoordinateTransformer
import cn.solarmoon.spark_core.physics.level.PhysicsWorld
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.math.Vector3f

/**
 * 表示在IEntityAnimatable上的IK设置的活动实例
 * 持有运行时状态（FabrikChain3D）并处理地面接触逻辑
 *
 * @property type 此IK组件的配置类型
 * @property chain 用于求解的运行时FabrikChain3D实例
 */
class IKComponent(
    val type: TypedIKComponent,
    val chain: FabrikChain3D,
    val host: IEntityAnimatable<*>
) {
    val chainName: String get() = type.chainName

    // 末端执行器骨骼名称
    val endBoneName: String get() = type.endBoneName

    // --- 地形交互属性 ---
    /** 如果为true，组件将通过射线检测尝试附着地面 */
    var stickToGround: Boolean = false

    /** 向下发射射线检测的垂直偏移量 */
    var groundCheckOffset: Vector3f = Vector3f(0f, 0.3f, 0f)

    /** 向下检测地面的最大距离 */
    var groundCheckDistance: Float = 1.0f

    /** 地面检测前的原始目标位置（通常来自动画/逻辑） */
    var desiredTargetPosition: Vector3f = Vector3f.ZERO.clone()
        private set // 仅允许通过更新方法设置

    /** 考虑地面接触后的实际目标位置（传递给IK求解器） */
    var actualTargetPosition: Vector3f = Vector3f.ZERO.clone()
        private set // 仅允许通过更新方法设置

    /** 如果地面检测找到有效地面，则为true */
    var isGrounded: Boolean = false
        private set // 仅允许通过更新方法设置

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
            // --- 射线投射过滤 ---
            // 根据您的地形物理设置调整此过滤器。
            // 示例：只与属于碰撞组 1 的静态物体碰撞（假设这就是地形）。
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
            // 找到地面：手动计算命中点并设置为实际目标
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
            // 可选：存储法线，用于后续方向调整
            // 或者在可用/需要时使用 hitNormalWorld
        } else {
            // 注意：我们保持当前的actualTargetPosition不变，因为它已经在updateTargetLocalPosition中设置了
            this.isGrounded = false
            // this.groundNormal = null
        }
    }

}