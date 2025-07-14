package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.npc_adapter.event.SparkPhysicsEvent.CollisionProcessedEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.neoforged.fml.ModList
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
            // 发送 SparkCore 通用事件
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NeoForge.EVENT_BUS.post(event)
            
            // 如果 CustomNPC 模组存在，则同时触发 CustomNPC 的脚本事件
            if (ModList.get().isLoaded("customnpcs")) {
                triggerCustomNpcEvent("preAttack", attacker, target, aBody, bBody, manifoldId)
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
        if (attacker.level().isClientSide) return false
        
        attacker.level().submitImmediateTask(PPhase.POST) {
            // 发送 SparkCore 通用事件
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NeoForge.EVENT_BUS.post(event)
            
            // 如果 CustomNPC 模组存在，则同时触发 CustomNPC 的脚本事件
            if (ModList.get().isLoaded("customnpcs")) {
                triggerCustomNpcEvent("doAttack", attacker, target, aBody, bBody, manifoldId)
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
        if (attacker.level().isClientSide) return
        
        attacker.level().submitImmediateTask(PPhase.POST) {
            // 发送 SparkCore 通用事件
            val event = CollisionProcessedEvent(aBody, bBody, manifoldId)
            NeoForge.EVENT_BUS.post(event)
            
            // 如果 CustomNPC 模组存在，则同时触发 CustomNPC 的脚本事件
            if (ModList.get().isLoaded("customnpcs")) {
                triggerCustomNpcEvent("postAttack", attacker, target, aBody, bBody, manifoldId)
            }
        }
    }

    /**
     * 安全地触发 CustomNPC 脚本事件
     */
    private fun triggerCustomNpcEvent(
        phase: String,
        attacker: Entity,
        target: Entity,
        aBody: PhysicsCollisionObject,
        bBody: PhysicsCollisionObject,
        manifoldId: Long
    ) {
        try {
            // 通过反射安全地调用 CustomNPC 的事件系统
            val eventHooksClass = Class.forName("noppes.npcs.EventHooks")
            val npcApiClass = Class.forName("noppes.npcs.api.NpcAPI")
            
            // 获取 NpcAPI 实例
            val npcApiInstance = npcApiClass.getMethod("Instance").invoke(null)
            
            // 获取 IWorld
            val getIWorldMethod = npcApiClass.getMethod("getIWorld", ServerLevel::class.java)
            val iWorld = getIWorldMethod.invoke(npcApiInstance, owner.level() as ServerLevel)
            
            // 获取 IPos
            val getIPosMethod = npcApiClass.getMethod("getIPos", Double::class.java, Double::class.java, Double::class.java)
            val iPos = getIPosMethod.invoke(npcApiInstance, owner.x, owner.y, owner.z)
            
            // 获取 IEntity
            val getIEntityMethod = npcApiClass.getMethod("getIEntity", Entity::class.java)
            val iEntity = getIEntityMethod.invoke(npcApiInstance, owner)
            
            // 构造事件参数
            val eventArgs = arrayOf<Any?>(
                phase, cbName, attacker.name.string, attacker.stringUUID, aBody.name, 
                target.name.string, target.stringUUID, bBody.name, manifoldId
            )
            
            // 调用脚本事件
            val onScriptTriggerEventMethod = eventHooksClass.getMethod(
                "onScriptTriggerEvent",
                Int::class.java,
                Class.forName("noppes.npcs.api.IWorld"),
                Class.forName("noppes.npcs.api.IPos"),
                Class.forName("noppes.npcs.api.IEntity"),
                Array<Any?>::class.java
            )
            
            onScriptTriggerEventMethod.invoke(null, eventId, iWorld, iPos, iEntity, eventArgs)
            
        } catch (e: Exception) {
            // 如果反射调用失败，记录警告但不影响主要功能
            SparkCore.LOGGER.warn(("Failed to trigger CustomNPC event: $e"))
        }
    }
}