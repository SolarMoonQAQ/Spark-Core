package cn.solarmoon.spark_core.physics.collision

import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap

// 存储冻结状态
private val frozenStates = ConcurrentHashMap<PhysicsCollisionObject, Boolean>()

/**
 * 物理体的冻结状态
 */
var PhysicsCollisionObject.isFrozen: Boolean
    get() = frozenStates.getOrDefault(this, false)
    set(value) {
        frozenStates[this] = value
    }

/**
* 获取物理体关联的实体（如果有）
*/
val PhysicsCollisionObject.entity: Entity?
   get() = this.owner as? Entity

/**
 * 清理冻结状态
 */
fun PhysicsCollisionObject.clearFrozenState() {
    frozenStates.remove(this)
}