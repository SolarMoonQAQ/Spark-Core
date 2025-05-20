package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.mozilla.javascript.Function
import java.util.UUID

object JSPhysicsHelper: JSComponent() {

    fun createCollisionBoxBoundToBone(animatable: IAnimatable<*>, boneName: String, size: Vec3, offset: Vec3) = createCollisionBoxBoundToBone(animatable, boneName, size, offset, null)
    /**
     * 创建绑定到骨骼的碰撞盒
     *
     * @param animatable 支持实体动画的对象（必须实现[IEntityAnimatable]接口）
     * @param boneName 目标骨骼名称，碰撞盒将跟随此骨骼运动
     * @param size 碰撞盒尺寸（单位：米），实际碰撞体会自动缩小一半以适应JBullet的尺寸计算方式
     * @param offset 相对于骨骼原点的偏移量（单位：米）
     * @param init 可选初始化函数，用于额外配置物理属性（lambda接收[PhysicsRigidBody]上下文）
     *
     * @return 配置完成的物理碰撞体实例，可用于后续碰撞检测或物理属性调整
     *
     * @throws IllegalArgumentException 当animatable参数不是有效的实体动画对象时抛出
     *
     * @note 默认配置：
     * - 禁用碰撞响应（仅作为触发器使用）
     * - 设置为运动学刚体（由动画系统驱动）
     * - 关闭所有碰撞组交互
     * - 禁用重力影响
     * - 自动绑定骨骼运动追踪器
     */
    fun createCollisionBoxBoundToBone(animatable: IAnimatable<*>, boneName: String, size: Vec3, offset: Vec3, init: Function?): PhysicsCollisionObject {
        val animatable = animatable as? IEntityAnimatable<*> ?: throw IllegalArgumentException("动画体必须是实体类型！")
        val entity = animatable.animatable
        return entity.bindBody(
            PhysicsRigidBody(UUID.randomUUID().toString(), entity, BoxCollisionShape(size.div(2.0).toBVector3f()))) {
            isContactResponse = false
            isKinematic = true
            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
            setGravity(Vector3f())
            addPhysicsTicker(MoveWithAnimatedBoneTicker(boneName, offset.toBVector3f()))
            init?.call(engine, this)
        }
    }

    fun getContactPosA(manifoldId: Long) = Vector3f().apply { ManifoldPoints.getPositionWorldOnA(manifoldId, this) }.toVec3()

    fun getContactPosB(manifoldId: Long) = Vector3f().apply { ManifoldPoints.getPositionWorldOnB(manifoldId, this) }.toVec3()

}