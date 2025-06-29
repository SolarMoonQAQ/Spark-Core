package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.datafixers.util.Either
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.common.NeoForge
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.ChildMode
import ru.nsk.kstatemachine.state.DataState
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.activeStates
import ru.nsk.kstatemachine.state.dataState
import ru.nsk.kstatemachine.state.initialChoiceDataState
import ru.nsk.kstatemachine.state.initialDataState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import java.util.Optional

object PlayerStateAnimMachine {

    object SwitchEvent: Event

    data class AnimStateData(
        val isBlend: Boolean = false,
        val immediate: Boolean = false,
        val value: () -> Int
    ) {
        companion object {
            fun optional(isBlend: Boolean = false, immediate: Boolean = false, value: () -> Int) = Optional.of(AnimStateData(isBlend, immediate, value))

            fun empty() = Optional.empty<AnimStateData>()
        }
    }

    @JvmStatic
    fun create(player: Player) = createStdLibStateMachine {
        val player = player as LocalPlayer

        val none = initialState("none")
        val base = state("base") {
            val origin = initialState("origin") {
                val land = dataState("land", AnimStateData.empty()) { setupMovementStates(player) }
                val crouch = dataState("crouch", AnimStateData.empty()) { setupMovementStates(player) }
                val swim = dataState("swim", AnimStateData.empty()) { setupMovementStates(player) }
                val fly = dataState("fly", AnimStateData.empty()) { setupMovementStates(player) }
                val sit = dataState("sit", AnimStateData.optional { 7 })
                val sleep = dataState("sleep", AnimStateData.optional { 7 })
                val fallFly = dataState("fall_fly", AnimStateData.optional { 7 })
                val fall = dataState("fall", AnimStateData.optional { 7 })

                val jumpLand = dataState("jump_land", AnimStateData.optional(true) { if (player.input.moveVector.length() > 0) 1 else 100 } )

                initialChoiceDataState<Optional<AnimStateData>> {
                    when {
                        player.isPassenger -> sit
                        player.isSleeping -> sleep
                        player.abilities.flying -> fly
                        player.isFallFlying -> fallFly
                        player.isSwimming -> swim
                        player.isCrouching -> crouch
                        (player.y - player.yOld) < 0.001 && player.isAboveGround(1.0) && !player.onGround() -> fall
                        activeStates().contains(fall) && !player.isAboveGround(1.0) -> jumpLand
                        else -> land
                    }
                }
            }
        }

        transitionOn<SwitchEvent> {
            targetState = {
                if (checkPlayingOtherAnim(player)) none else base
            }
        }

        onStateEntry { s, b ->
            val sName = s.name ?: return@onStateEntry
            if (s !is DataState<*>) return@onStateEntry
            val op = s.data
            if (op !is Optional<*> || op.isEmpty) return@onStateEntry
            val data = op.get()
            if (data !is AnimStateData) return@onStateEntry
            SparkCore.LOGGER.info(sName)
            SparkRegistries.TYPED_ANIMATION.get(ResourceLocation.parse("${SparkCore.MOD_ID}:${sName}"))?.let {
                val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.PlayerState(player, it, s, data))
                if (event.isCanceled) return@let
                val anim = event.newAnim ?: event.originAnim
                val value = event.data.value()
                if (event.data.isBlend) {
                    val bid = "AnimState:$sName"
                    anim.blend(player, bid, value.toDouble())
                    anim.blendToServer(player.id, bid, value.toDouble())
                } else {
                    anim.play(player, value)
                    anim.playToServer(player.id, value)
                }
            }
        }
    }

    private suspend fun IState.setupMovementStates(player: LocalPlayer) {
        val idle = dataState("$name.idle", AnimStateData.optional { 7 })
        val move = dataState("$name.move", AnimStateData.optional { 7 })
        val moveBack = dataState("$name.move_back", AnimStateData.optional { 7 })
        val sprint = dataState("$name.sprint", AnimStateData.optional { 7 })

        initialChoiceDataState {
            when {
                player.input == null -> idle
                player.isSprinting -> sprint
                player.input.down && player.input.moveVector.length() > 0 -> moveBack
                player.input.moveVector.length() > 0 -> move
                else -> idle
            }
        }
    }

    fun checkPlayingOtherAnim(animatable: IAnimatable<*>): Boolean {
        val controller = animatable.animController
        val animNow = controller.getPlayingAnim() ?: return false
        return animNow.name.substringBefore(".") != "state"
    }

    @JvmStatic
    fun progress(player: Player) {
        player.animStateMachine?.processEventBlocking(SwitchEvent)
    }

}