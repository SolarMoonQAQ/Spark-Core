package cn.solarmoon.spark_core.physics.entity

import cn.solarmoon.spark_core.physics.host.PhysicsHost
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import kotlin.reflect.KClass

/**
 * Entity的物理扩展函数，提供更便捷的物理体管理
 */

/**
 * 设置Entity的物理体冻结状态
 */
fun Entity.setPhysicsFrozen(frozen: Boolean) {
    EntityPhysicsManager.setEntityFrozen(this, frozen)
}

/**
 * 获取Entity的物理体冻结状态
 */
fun Entity.isPhysicsFrozen(): Boolean {
    return EntityPhysicsManager.isEntityFrozen(this)
}

/**
 * 切换Entity的物理体冻结状态
 * @return 切换后的状态
 */
fun Entity.togglePhysicsFrozen(): Boolean {
    return EntityPhysicsManager.toggleEntityFrozen(this)
}

/**
 * 为实体添加物理体，并自动处理冻结状态
 */
fun <T: PhysicsCollisionObject> Entity.addPhysicsBody(
    body: T,
    name: String,
    allowOverride: Boolean = false
): T? {
    
    return EntityPhysicsManager.addBodyToEntity(
        this,
        body,
        name,
        this.physicsLevel,
        allowOverride
    )
}

/**
 * 获取指定名称和类型的物理体
 */
fun <T : PhysicsCollisionObject> Entity.getPhysicsBody(name: String, type: KClass<T>): T? {
    return this.getBody(name, type)
}

/**
 * 移除实体的物理体
 */
fun Entity.removePhysicsBody(name: String) {
    this.removeBody(name)
}

/**
 * 移除实体的所有物理体
 */
fun Entity.removeAllPhysicsBodies() {
    this.removeAllBodies()
    // 清除冻结状态
    EntityPhysicsManager.resetEntityFrozen(this)
}

/**
 * 获取实体的所有物理体
 */
fun Entity.getAllPhysicsBodies(): Collection<PhysicsCollisionObject>? {
    return this.getAllBodies()
}