package cn.solarmoon.spark_core.animation.anim.state

import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import ru.nsk.kstatemachine.statemachine.StateMachine
import java.util.concurrent.ConcurrentHashMap

object AnimStateMachineManager {

    private val serverStateMachines = hashMapOf<String, StateMachine>()
    private val clientStateMachines = hashMapOf<String, StateMachine>()

    private val entityAnimationOverrides: MutableMap<String, MutableMap<String, TypedAnimation>> = ConcurrentHashMap()

    init {
        NeoForge.EVENT_BUS.register(this)
    }

    fun putStateMachine(entity: Entity, level: Level, machine: StateMachine) {
        if (level.isClientSide) {
            clientStateMachines[entity.stringUUID] = machine
        } else {
            serverStateMachines[entity.stringUUID] = machine
        }
    }

    fun getStateMachine(entity: Entity) = if (entity.level().isClientSide) clientStateMachines[entity.stringUUID] else serverStateMachines[entity.stringUUID]

    fun registerEntityAnimationOverride(UUID: String, animationStateKey: String, animation: TypedAnimation) {
        entityAnimationOverrides.computeIfAbsent(UUID) { ConcurrentHashMap<String, TypedAnimation>() }[animationStateKey] = animation
    }

    fun unregisterEntityAnimationOverride(UUID: String, animationStateKey: String) {
        val entityOverrides = entityAnimationOverrides[UUID]
        entityOverrides?.remove(animationStateKey)
        if (entityOverrides?.isEmpty() == true) {
            entityAnimationOverrides.remove(UUID)
        }
    }

    fun clearAllEntityAnimationOverrides(UUID: String) {
        entityAnimationOverrides.remove(UUID)
    }

    fun getEntityAnimationOverride(UUID: String, animationStateKey: String): TypedAnimation? {
        val typedAnimation = entityAnimationOverrides[UUID]?.get(animationStateKey)
        if (typedAnimation != null) {
            // Check if the TypedAnimation is still registered
            if (SparkRegistries.TYPED_ANIMATION.getKey(typedAnimation) == null) {
                // Animation is no longer valid, remove it and return null
                unregisterEntityAnimationOverride(UUID, animationStateKey)
                return null
            }
        }
        return typedAnimation
    }

    @SubscribeEvent
    fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        clearAllEntityAnimationOverrides(event.entity.stringUUID)
        // Also clear state machines if they exist for this entity
        serverStateMachines.remove(event.entity.stringUUID)
        clientStateMachines.remove(event.entity.stringUUID)
    }
}