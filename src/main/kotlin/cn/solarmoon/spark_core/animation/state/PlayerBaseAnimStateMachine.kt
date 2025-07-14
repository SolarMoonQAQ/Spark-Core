package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendData
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.common.NeoForge
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.activeStates
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.processEventBlocking

class PlayerBaseAnimStateMachine(
    val player: Player
) {

    object SwitchEvent: Event

    val baseMachine = if (!player.isLocalPlayer) null else createStdLibStateMachine {
        val player = player as LocalPlayer

        val none = initialState("none")
        val base = state("base") {
            val land = state("land") { setupMovementStates(player) }
            val crouch = state("crouch") { setupMovementStates(player) }
            val swim = state("swim") { setupMovementStates(player) }
            val fly = state("fly") { setupMovementStates(player) }
            val sit = state("sit") { payload = MainPlayDataProvider { 7 } }
            val sleep = state("sleep") { payload = MainPlayDataProvider { 7 } }
            val fallFly = state("fall_fly") { payload = MainPlayDataProvider { 7 } }
            val fall = state("fall") { payload = MainPlayDataProvider { 7 } }

            val jumpLand = state("jump_land") { payload = BlendDataProvider { BlendData(if (player.input.moveVector.length() > 0) 1.0 else 100.0) } }

            initialChoiceState {
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

        transitionOn<SwitchEvent> {
            targetState = {
                if (checkPlayingOtherAnim()) none else base
            }
        }

        onStateEntry { s, b ->
            val stateName = s.name ?: return@onStateEntry
            val animName = "state.$stateName"
            SparkCore.LOGGER.info("payload:${s.payload}")
            s.playRelativeAnim(animName)
        }
    }

    private fun IState.playRelativeAnim(animName: String) {
        val data = payload
        if (data !is AnimPlayDataProvider) return
        val animationPath = SparkResourcePathBuilder.buildAnimationPath("spark_core", "spark_core", "player", animName)
        SparkRegistries.TYPED_ANIMATION.get(animationPath)?.let {
            val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.PlayerState(player, it, this, data))
            if (event.isCanceled) return@let
            val anim = event.newAnim ?: event.originAnim
            val data = event.data

            if (data is BlendDataProvider) {
                val data = data.blendData()
                anim.blend(player, data)
                anim.blendToServer(player.id, data)
            } else if (data is MainPlayDataProvider) {
                val tt = data.transTime()
                anim.play(player, tt)
                anim.playToServer(player.id, tt)
            }
        }
    }

    private suspend fun IState.setupMovementStates(player: LocalPlayer) {
        val idle = state("$name.idle") { payload = MainPlayDataProvider { 7 } }
        val move = state("$name.move") { payload = MainPlayDataProvider { 7 } }
        val moveBack = state("$name.move_back") { payload = MainPlayDataProvider { 7 } }
        val sprint = state("$name.sprint") { payload = MainPlayDataProvider { 7 } }

        initialChoiceState {
            when {
                player.input == null -> idle
                player.isSprinting -> sprint
                player.input.down && player.input.moveVector.length() > 0 -> moveBack
                player.input.moveVector.length() > 0 -> move
                else -> idle
            }
        }
    }

    fun checkPlayingOtherAnim(): Boolean {
        val controller = player.animController
        val animNow = controller.getPlayingAnim() ?: return false
        return animNow.animIndex.name.substringBefore(".") != "state"
    }

    fun progress() {
        baseMachine?.processEventBlocking(SwitchEvent)
    }

}