package cn.solarmoon.spark_core.animation.presets

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.entity.isFalling
import cn.solarmoon.spark_core.entity.isMoving
import cn.solarmoon.spark_core.entity.moveBackCheck
import cn.solarmoon.spark_core.entity.moveCheck
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
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
    fun create(player: Player) = createStdLibStateMachine(creationArguments = buildCreationArguments { isUndoEnabled = true }) {
        val player = player as LocalPlayer

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
                Minecraft.getInstance().player == null -> none
                checkPlayingOtherAnim(player) -> none
                player.vehicle != null -> sit
                player.isSleeping -> sleeping
                player.isSwimming -> swimming
                player.isFallFlying -> fallFlying
                player.abilities.flying && player.moveCheck() -> flyMove
                player.abilities.flying -> fly
                player.isInWater && player.isFalling() -> swimmingIdle
                player.isFalling() && player.isAboveGround(0.75) -> fall
                player.isCrouching && player.moveCheck() -> crouchingMove
                player.isCrouching -> crouching
                player.isSprinting -> sprinting
                player.moveBackCheck() && player.isMoving() -> walkBack
                player.isMoving() -> walk
                else -> idle
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
                anim.play(player, event.transitionTime)
                anim.syncToServer(player.id, event.transitionTime)
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