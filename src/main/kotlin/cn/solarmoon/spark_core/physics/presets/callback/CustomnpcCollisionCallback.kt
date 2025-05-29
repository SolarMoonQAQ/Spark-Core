package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.getFunctionMember
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
class CustomnpcCollisionCallback : HitReactionCollisionCallback {
    override val attackSystem: AttackSystem = AttackSystem()

    override fun preAttack(
        isFirst: Boolean,
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ) {
        if (!isFirst) return
        if ((attacker.level().isClientSide || !(attacker is EntityCustomNpc || target is EntityCustomNpc))) return
        attacker.level().submitImmediateTask(PPhase.POST) {
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NpcAPI.Instance().events().post(event)
            // 触发脚本事件

            if (attacker is EntityCustomNpc) {
                EventHooks.onScriptTriggerEvent(
                    1001,
                    NpcAPI.Instance().getIWorld(attacker.level() as ServerLevel),
                    NpcAPI.Instance().getIPos(attacker.x, attacker.y, attacker.z),
                    NpcAPI.Instance().getIEntity(attacker),
                    arrayOf<Any?>("preAttack",attacker.name.string+"_"+aBody.name, target.name.string+"_"+bBody.name, manifoldId)
                )
            }else {
                EventHooks.onScriptTriggerEvent(
                    1001,
                    NpcAPI.Instance().getIWorld(target.level() as ServerLevel),
                    NpcAPI.Instance().getIPos(target.x, target.y, target.z),
                    NpcAPI.Instance().getIEntity(target),
                    arrayOf<Any?>("preAttack", attacker.name.string+"_"+aBody.name, target.name.string+"_"+bBody.name, manifoldId)
                )
            }
        }
    }

    override fun doAttack(
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ): Boolean {
        if (attacker.level().isClientSide || !(attacker is EntityCustomNpc || target is EntityCustomNpc)) return false
        attacker.level().submitImmediateTask(PPhase.POST) {
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NpcAPI.Instance().events().post(event)
            // 触发脚本事件
            if (attacker is EntityCustomNpc) {
                EventHooks.onScriptTriggerEvent(
                    1001,
                    NpcAPI.Instance().getIWorld(attacker.level() as ServerLevel),
                    NpcAPI.Instance().getIPos(attacker.x, attacker.y, attacker.z),
                    NpcAPI.Instance().getIEntity(attacker),
                    arrayOf<Any?>("doAttack",attacker.name.string+"_"+aBody.name, attacker.name.string+"_"+bBody.name, manifoldId)
                )
            }else {
                EventHooks.onScriptTriggerEvent(
                    1001,
                    NpcAPI.Instance().getIWorld(target.level() as ServerLevel),
                    NpcAPI.Instance().getIPos(target.x, target.y, target.z),
                    NpcAPI.Instance().getIEntity(target),
                    arrayOf<Any?>("doAttack", target.name.string+"_"+aBody.name, target.name.string+"_"+bBody.name, manifoldId)
                )
            }
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
        if (attacker.level().isClientSide || !(attacker is EntityCustomNpc || target is EntityCustomNpc)) return
        attacker.level().submitImmediateTask(PPhase.POST) {
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NpcAPI.Instance().events().post(event)
            // 触发脚本事件
            if (attacker is EntityCustomNpc) {
                EventHooks.onScriptTriggerEvent(
                    1001,
                    NpcAPI.Instance().getIWorld(attacker.level() as ServerLevel),
                    NpcAPI.Instance().getIPos(attacker.x, attacker.y, attacker.z),
                    NpcAPI.Instance().getIEntity(attacker),
                    arrayOf<Any?>("postAttack",attacker.name.string+"_"+aBody.name, attacker.name.string+"_"+bBody.name, manifoldId)
                )
            }else {
                EventHooks.onScriptTriggerEvent(
                    1001,
                    NpcAPI.Instance().getIWorld(target.level() as ServerLevel),
                    NpcAPI.Instance().getIPos(target.x, target.y, target.z),
                    NpcAPI.Instance().getIEntity(target),
                    arrayOf<Any?>("postAttack", target.name.string+"_"+aBody.name, target.name.string+"_"+bBody.name, manifoldId)
                )
            }
        }
    }


}