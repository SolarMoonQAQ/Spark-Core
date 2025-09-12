package cn.solarmoon.spark_core.physics.entity

import cn.solarmoon.spark_core.physics.collision.isFrozen
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap

/**
 * 提供Entity级别的物理体管理功能，包括冻结状态管理
 */
object EntityPhysicsManager {

    // 为每个Entity存储其关联的物理体冻结状态
    private val entityFrozenStates = ConcurrentHashMap<Entity, Boolean>()
    
    // 存储各物理体的原始激活状态，用于恢复
    private val originalBodyStates = ConcurrentHashMap<PhysicsCollisionObject, Boolean>()
    
    /**
     * 设置Entity的物理冻结状态
     * @param entity 目标实体
     * @param frozen 是否冻结
     */
    fun setEntityFrozen(entity: Entity, frozen: Boolean) {
        
        entityFrozenStates[entity] = frozen
        
        // 获取实体关联的所有物理体
        val bodies = entity.getAllBodies()
        
        entity.physicsLevel.submitImmediateTask {
            for (body in bodies) {
                setBodyFrozen(body, frozen)
            }
        }
    }
    
    /**
     * 获取Entity的物理冻结状态
     * @param entity 目标实体
     * @return 是否被冻结
     */
    fun isEntityFrozen(entity: Entity): Boolean {
        return entityFrozenStates.getOrDefault(entity, false)
    }
    
    /**
     * 将物理体添加到Entity并应用当前的冻结状态
     * @param entity 目标实体
     * @param body 物理体
     * @param name 物理体名称
     * @param physicsLevel 物理世界
     * @param allowOverride 是否允许覆盖
     */
    fun <T: PhysicsCollisionObject> addBodyToEntity(
        entity: Entity,
        body: T,
        name: String,
        physicsLevel: PhysicsLevel,
        allowOverride: Boolean = false
    ): T {
        // 应用当前的冻结状态
        val isFrozen = isEntityFrozen(entity)
        
        return entity.bindBody(body, physicsLevel, allowOverride) {
            body.name = name
            body.owner = entity
            
            // 如果实体已冻结，则立即冻结新添加的物理体
            if (isFrozen) {
                setBodyFrozen(body, true)
            }
        }
    }
    
    /**
     * 设置单个物理体的冻结状态
     * @param body 目标物理体
     * @param frozen 是否冻结
     */
    private fun setBodyFrozen(body: PhysicsCollisionObject, frozen: Boolean) {
        // 保存原始状态用于恢复
        if (!originalBodyStates.containsKey(body)) {
            if (body is PhysicsRigidBody) {
                originalBodyStates[body] = body.isActive
            }
        }
        
        // 设置冻结状态
        if (body is PhysicsRigidBody) {
            if (frozen) {
                // 冻结刚体
                body.activate(false)
                body.setAngularVelocity(Vector3f())
                body.setLinearVelocity(Vector3f())
            } else {
                // 恢复到原始状态
                val originalState = originalBodyStates[body] ?: true
                body.activate(originalState)
            }
        }
        
        // 保存冻结状态
        body.isFrozen = frozen
    }
    
    /**
     * 切换Entity的物理冻结状态
     * @param entity 目标实体
     * @return 切换后的冻结状态
     */
    fun toggleEntityFrozen(entity: Entity): Boolean {
        val newState = !isEntityFrozen(entity)
        setEntityFrozen(entity, newState)
        return newState
    }
    
    /**
     * 重置Entity的物理冻结状态
     * @param entity 目标实体
     */
    fun resetEntityFrozen(entity: Entity) {
        setEntityFrozen(entity, false)
        entityFrozenStates.remove(entity)
        val bodies = entity.getAllBodies() ?: return

        for (body in bodies) {
            originalBodyStates.remove(body)
        }
    }
}