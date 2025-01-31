package cn.solarmoon.spark_core.animation.preset_anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.vanilla.asAnimatable
import cn.solarmoon.spark_core.entity.isFalling
import cn.solarmoon.spark_core.entity.moveBackCheck
import cn.solarmoon.spark_core.entity.moveCheck
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.NeoForge
import ru.nsk.kstatemachine.event.DataEvent
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.buildCreationArguments
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.onStateExit

object PlayerStateAnimMachine {

    class SwitchEvent: DataEvent<Boolean> { override var data = true }

    object ResetEvent: Event

    @JvmStatic
    fun create(player: LocalPlayer) = createStdLibStateMachine(creationArguments = buildCreationArguments { isUndoEnabled = true }) {
        val none = state("none")
        addState(EntityStates.Idle)
        addState(EntityStates.Walk)
        addState(EntityStates.WalkBack)
        addState(EntityStates.Sprinting)
        addState(EntityStates.Fly)
        addState(EntityStates.FlyMove)
        addState(EntityStates.Crouching)
        addState(EntityStates.CrouchingMove)
        addState(EntityStates.Fall)
        addState(EntityStates.Sit)
        addState(EntityStates.FallFlying)
        addState(EntityStates.Sleeping)
        addState(EntityStates.Swimming)
        addState(EntityStates.SwimmingIdle)

        val choice = initialChoiceState("choice") {
            when {
                Minecraft.getInstance().player == null -> none
                checkPlayingOtherAnim(player.asAnimatable()) -> none
                player.vehicle != null -> EntityStates.Sit
                player.isSleeping -> EntityStates.Sleeping
                player.isSwimming -> EntityStates.Swimming
                player.isFallFlying -> EntityStates.FallFlying
                player.abilities.flying && player.moveCheck() -> EntityStates.FlyMove
                player.abilities.flying -> EntityStates.Fly
                player.isInWater && player.isFalling() -> EntityStates.SwimmingIdle
                player.isFalling() -> EntityStates.Fall
                player.isCrouching && player.moveCheck() -> EntityStates.CrouchingMove
                player.isCrouching -> EntityStates.Crouching
                player.isSprinting -> EntityStates.Sprinting
                player.moveBackCheck() -> EntityStates.WalkBack
                player.moveCheck() -> EntityStates.Walk
                else -> EntityStates.Idle
            }
        }

        onStateEntry { s, b ->
            if ((b.event as? SwitchEvent)?.data == false) return@onStateEntry
            if (s == none) return@onStateEntry
            val sName = s.name ?: return@onStateEntry
            SparkRegistries.TYPED_ANIMATION.get(ResourceLocation.parse(sName))?.let {
                val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.PlayerState(player, it, s, 7))
                if (event.isCanceled) return@let
                val anim = event.newAnim ?: event.originAnim
                anim.play(player.asAnimatable(), event.transitionTime)
                anim.syncToServer(player.id, event.transitionTime)
            }
        }

        onStateExit { old, trans ->
            if (old == EntityStates.WalkBack && trans.direction.targetState == EntityStates.Walk) {
                (trans.event as? SwitchEvent)?.data = false
            }
        }

        transition<SwitchEvent> {
            targetState = choice
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