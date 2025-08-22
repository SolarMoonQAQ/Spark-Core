package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.anim.play.layer.DefaultLayer
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.common.SparkStateMachineRegister
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import cn.solarmoon.spark_core.state_machine.StateMachineHandler
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.LadderBlock
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.onStateExit
import ru.nsk.kstatemachine.statemachine.processEventBlocking

class PlayerBaseAnimStateMachine(
    val player: Player
): StateMachineHandler {

    override var isActive = true

    object JumpEvent: Event

    object FallEvent: Event

    object ResetEvent: Event

    object SwitchEvent: Event

    var lastState: IState? = null

    override val machine = if (!player.isLocalPlayer) null else createStdLibStateMachine {
        val player = player as LocalPlayer

        val none = initialState("none")
        val base = state("base") {
            val land = state("land") { setupMovementStates(player) }
            val crouch = state("crouch") { setupMovementStates(player) }
            val swim = state("swim") { setupMovementStates(player) }
            val fly = state("fly") { setupMovementStates(player) }
            val climb = state("climb") {
                val idle = state("$name.idle") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
                val move = state("$name.move") { payload = AnimPlayDataProvider { if (it == idle) AnimLayerData(enterTransitionTime = 0) else AnimLayerData(enterTransitionTime = 7) } }
                initialChoiceState {
                    when {
                        !player.isSuppressingSlidingDownLadder && !player.onGround() || player.input.moveVector.length() > 0  -> move
                        else -> idle
                    }
                }
            }
            val sit = state("sit") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
            val sleep = state("sleep") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
            val fallFly = state("fall_fly") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
            val fall = state("fall") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
            val jump = state("jump") { payload = AnimPlayDataProvider(DefaultLayer.MAIN_LAYER) { AnimLayerData(enterTransitionTime = 0) } }
            val jumpLand = state("jump_land") { payload = AnimPlayDataProvider(DefaultLayer.MAIN_LAYER) { AnimLayerData(weight = if (player.input.moveVector.length() > 0) 0.5 else 1.0, enterTransitionTime = 0) } }

            initialChoiceState {
                when {
                    player.isPassenger -> sit
                    player.isSleeping -> sleep
                    player.abilities.flying -> fly
                    player.onClimbable() -> climb
                    player.isFallFlying -> fallFly
                    (player.isUnderWater || player.canStartSwimming()) -> swim
                    player.isCrouching -> crouch
                    (player.y - player.yOld) < 0.001 && player.isAboveGround(1.0) && !player.onGround() && !player.isInFluidType -> fall
                    else -> land
                }
            }

            transitionOn<JumpEvent> {
                targetState = { jump }
            }

            transitionOn<FallEvent> {
                targetState = { jumpLand }
            }
        }

        transitionOn<SwitchEvent> {
            targetState = {
                base
            }
        }

        transitionOn<ResetEvent> {
            targetState = { none }
        }

        onStateExit { s, t ->
            lastState = s
        }

        onStateEntry { s, t ->
            val stateName = s.name ?: return@onStateEntry
            val animName = "state.$stateName"
            SparkCore.LOGGER.info("payload:${s.payload}")
            s.playRelativeAnim(animName)
        }
    }

    private fun IState.playRelativeAnim(animName: String) {
        player.isBlocking
        val data = payload
        if (data !is AnimPlayDataProvider) return
        SparkCore.LOGGER.info(animName)
        val animationPath = SparkResourcePathBuilder.buildAnimationPath("spark_core", "spark_core", "player", animName)
        SparkRegistries.TYPED_ANIMATION.get(animationPath)?.let {
            val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.PlayerState(player, it, this, data))
            if (event.isCanceled) return@let
            val anim = event.newAnim ?: event.originAnim
            val data = event.data

            anim.play(player, data.layerId, data.data(lastState))
            anim.playToServer(player, data.layerId, data.data(lastState))
        }
    }

    private suspend fun IState.setupMovementStates(player: LocalPlayer) {
        val idle = state("$name.idle") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
        val move = state("$name.move") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
        val moveBack = state("$name.move_back") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }
        val sprint = state("$name.sprint") { payload = AnimPlayDataProvider { AnimLayerData(enterTransitionTime = 7) } }

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

    override fun progress() {
        machine?.processEventBlocking(SwitchEvent)
    }

    object Modifier {
        @SubscribeEvent
        private fun entityTick(event: EntityTickEvent.Post) {
            val player = event.entity
            val level = player.level()
            if (player is Player && player.onClimbable()) {
                player.lastClimbablePos.ifPresent { pos ->
                    val ladder = level.getBlockState(pos)
                    ladder.getOptionalValue(LadderBlock.FACING).ifPresent { facing ->
                        val facingRot = facing.opposite.toYRot()
                        // 限制爬梯朝向
                        player.setYBodyRot(facingRot)
                        // 限制头角度在一定范围
                        var angleDiff = Mth.degreesDifference(facingRot, player.yHeadRot) // 归一化差值
                        angleDiff = angleDiff.coerceIn(-90f, 90f)                // 应用限制范围
                        player.yHeadRot = facingRot + angleDiff                 // 重新组合角度
                    }
                }
            }
        }

        @SubscribeEvent
        private fun jump(event: LivingEvent.LivingJumpEvent) {
            val player = event.entity
            if (player is Player && player.isLocalPlayer) player.getStateMachineHandler(SparkStateMachineRegister.PLAYER_BASE_STATE)?.machine?.processEventBlocking(JumpEvent)
        }

//        @SubscribeEvent
//        private fun fall(event: PlayerFallEvent) {
//            val player = event.entity
//            if (player.isLocalPlayer && event.distance >= 1.0) player.getStateMachineHandler(SparkStateMachineRegister.PLAYER_BASE_STATE)?.machine?.processEventBlocking(FallEvent)
//        }
    }

}