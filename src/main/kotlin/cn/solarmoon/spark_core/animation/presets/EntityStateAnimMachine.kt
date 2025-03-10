package cn.solarmoon.spark_core.animation.presets

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.entity.isFalling
import cn.solarmoon.spark_core.entity.moveBackCheck
import cn.solarmoon.spark_core.entity.moveCheck
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.neoforged.neoforge.common.NeoForge
import ru.nsk.kstatemachine.event.DataEvent
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.buildCreationArguments
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry

object EntityStateAnimMachine {

    class SwitchEvent: DataEvent<Boolean> { override var data = true }

    object ResetEvent: Event

    @JvmStatic
    fun create(animatable: IEntityAnimatable<*>, entity: LivingEntity, usePlayerAnim: Boolean) = createStdLibStateMachine(
        creationArguments = buildCreationArguments {
            isUndoEnabled = true
        }
    ) {
        val none = state("none")
        val idle = addState(EntityStates.Idle())
        val walk = addState(EntityStates.Walk())
        val walkBack = addState(EntityStates.WalkBack())
        val sprinting = addState(EntityStates.Sprinting())
        val fly = addState(EntityStates.Fly())
        val flyMove = addState(EntityStates.FlyMove())
        val crouching = addState(EntityStates.Crouching())
        val crouchingMove = addState(EntityStates.CrouchingMove())
        val fall = addState(EntityStates.Fall())
        val sit = addState(EntityStates.Sit())
        val fallFlying = addState(EntityStates.FallFlying())
        val sleeping = addState(EntityStates.Sleeping())
        val swimming = addState(EntityStates.Swimming())
        val swimmingIdle = addState(EntityStates.SwimmingIdle())

        val choice = initialChoiceState("choice") {
            when {
                checkPlayingOtherAnim(animatable) -> none
                entity.vehicle != null -> sit
                entity.isSleeping -> sleeping
                entity.isSwimming -> swimming
                entity.isFallFlying -> fallFlying
                entity.isInWater && entity.isFalling() -> swimmingIdle
                entity.isFalling() && entity.isAboveGround(0.75) -> fall
                entity.isCrouching && entity.moveCheck() -> crouchingMove
                entity.isCrouching -> crouching
                entity.isSprinting -> sprinting
                entity.moveBackCheck() && entity.moveCheck() -> walkBack
                entity.moveCheck() -> walk
                else -> idle
            }
        }

        onStateEntry { s, b ->
            val events = b.event as? SwitchEvent ?: return@onStateEntry
            if (events.data == false) return@onStateEntry
            if (s == none) return@onStateEntry
            val sName = s.name ?: return@onStateEntry
            SparkRegistries.TYPED_ANIMATION.get(ResourceLocation.parse(sName))?.let {
                val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.EntityState(entity, it, s, 7, usePlayerAnim))
                if (event.isCanceled) return@let
                val anim = event.newAnim ?: event.originAnim
                anim.play(animatable, event.transitionTime, !event.usePlayerAnim)
                anim.syncToClient(entity.id, event.transitionTime, !event.usePlayerAnim)
            }
        }

        var lock: Int = 0

        transition<SwitchEvent> {
            guard = {
                val check = lock > 0
                lock++
                check
            }
            targetState = choice
            lock = 0
        }

        transition<ResetEvent> {
            targetState = none
        }
    }

    fun checkPlayingOtherAnim(animatable: IAnimatable<*>): Boolean {
        val controller = animatable.animController
        val animNow = controller.getPlayingAnim() ?: return false
        return animNow.name.substringBefore("/") != "EntityState"
    }

}