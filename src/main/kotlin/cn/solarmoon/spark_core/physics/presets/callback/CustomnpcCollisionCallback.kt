package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.npc_adapter.event.SparkPhysicsEvent.CollisionProcessedEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import noppes.npcs.EventHooks
import noppes.npcs.api.NpcAPI
import noppes.npcs.entity.EntityCustomNpc


/**
 * 处理实体受击时的精确碰撞回调。
 *
 * 负责检测有效的受击事件，提取碰撞信息，并触发相应的受击事件。
 */

class CustomnpcCollisionCallback @JvmOverloads constructor(
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
        isFirst: Boolean,
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ) {
        if (!isFirst) return
        if (owner.level().isClientSide) return
        attacker.level().submitImmediateTask(PPhase.POST) {
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NpcAPI.Instance().events().post(event)
            // 触发脚本事件
            EventHooks.onScriptTriggerEvent(
                eventId,
                NpcAPI.Instance().getIWorld(owner.level() as ServerLevel),
                NpcAPI.Instance().getIPos(owner.x, owner.y, owner.z),
                NpcAPI.Instance().getIEntity(owner),
                arrayOf<Any?>("preAttack", cbName, attacker.name.string, attacker.stringUUID, aBody.name, target.name.string, target.stringUUID, bBody.name, manifoldId)
            )
        }
    }

    override fun doAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ): Boolean {
        if (attacker.level().isClientSide) return false
        attacker.level().submitImmediateTask(PPhase.POST) {
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NpcAPI.Instance().events().post(event)
            // 触发脚本事件
            EventHooks.onScriptTriggerEvent(
                eventId,
                NpcAPI.Instance().getIWorld(owner.level() as ServerLevel),
                NpcAPI.Instance().getIPos(owner.x, owner.y, owner.z),
                NpcAPI.Instance().getIEntity(owner),
                arrayOf<Any?>("doAttack", cbName, attacker.name.string, attacker.stringUUID, aBody.name, target.name.string, target.stringUUID, bBody.name, manifoldId)
            )
        }
        return true
    }

    override fun postAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ) {
        if (attacker.level().isClientSide) return
        attacker.level().submitImmediateTask(PPhase.POST) {
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NpcAPI.Instance().events().post(event)
            // 触发脚本事件
            EventHooks.onScriptTriggerEvent(
                eventId,
                NpcAPI.Instance().getIWorld(owner.level() as ServerLevel),
                NpcAPI.Instance().getIPos(owner.x, owner.y, owner.z),
                NpcAPI.Instance().getIEntity(owner),
                arrayOf<Any?>("postAttack",cbName, attacker.name.string, attacker.stringUUID, aBody.name, target.name.string, target.stringUUID, bBody.name, manifoldId)
            )
        }
    }
}