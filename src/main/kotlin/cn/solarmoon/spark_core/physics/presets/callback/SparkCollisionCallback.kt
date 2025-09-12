package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.physics.collision.ManifoldPoint
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.npc_adapter.event.SparkPhysicsEvent.CollisionProcessedEvent
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.common.NeoForge

/**
 * 通用的碰撞回调处理器，替代 CustomnpcCollisionCallback
 * 
 * 这个类可以在有或没有 CustomNPC 模组的情况下工作
 */
class SparkCollisionCallback @JvmOverloads constructor(
    val cbName: String,
    val owner: Entity,
    collisionBoxId: String,
    autoResetEnabled: Boolean = false,
    resetAfterTicks: Int = 0,
    val eventId: Int = 1001,
) : HitReactionCollisionCallback {

    override val attackSystem: AttackSystem = AttackSystem()

    init {
        // 在回调初始化时，直接初始化 AttackSystem 的上下文
        attackSystem.initializeContext(owner, collisionBoxId, cbName, autoResetEnabled = autoResetEnabled, resetAfterTicks = resetAfterTicks)
    }

    override fun preAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        aPoint: ManifoldPoint,
        bPoint: ManifoldPoint,
        manifoldId: Long
    ) {
        if (owner.level().isClientSide) return
        
        attacker.level().submitImmediateTask {
            // 发送 SparkCore 通用事件
            val event = CollisionProcessedEvent(aBody, bBody, aPoint, bPoint, manifoldId)
            NeoForge.EVENT_BUS.post(event)
        }
    }

    override fun doAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        aPoint: ManifoldPoint,
        bPoint: ManifoldPoint,
        manifoldId: Long
    ): Boolean {
        if (attacker.level().isClientSide) return false
        
        attacker.level().submitImmediateTask {
            // 发送 SparkCore 通用事件
            val event = CollisionProcessedEvent(aBody, bBody, aPoint, bPoint, manifoldId)
            NeoForge.EVENT_BUS.post(event)
        }
        return true
    }

    override fun postAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        aPoint: ManifoldPoint,
        bPoint: ManifoldPoint,
        manifoldId: Long
    ) {
        if (attacker.level().isClientSide) return
        
        attacker.level().submitImmediateTask {
            // 发送 SparkCore 通用事件
            val event = CollisionProcessedEvent(aBody, bBody, aPoint, bPoint, manifoldId)
            NeoForge.EVENT_BUS.post(event)
        }
    }

}