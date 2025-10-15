package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.animInstance
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.entity.groundMovement
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.entity.moveBackCheck
import cn.solarmoon.spark_core.entity.moveCheck
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkStateMachineRegister
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

    override val machine = createStdLibStateMachine {
        val none = initialState("none")
        val base = state("base") {
            val land = state("land") { setupMovementStates() }
            val crouch = state("crouch") { setupMovementStates() }
            val swim = state("swim") { setupMovementStates() }
            val fly = state("fly") { setupMovementStates() }
            val climb = state("climb") {
                val idle = state("$name.idle")
                val move = state("$name.move")
                initialChoiceState {
                    when {
                        !player.isSuppressingSlidingDownLadder && !player.onGround() || player.groundMovement().y != 0.0  -> move
                        else -> idle
                    }
                }
            }
            val sit = state("sit")
            val sleep = state("sleep")
            val fallFly = state("fall_fly")
            val fall = state("fall")
            val jump = state("jump") { payload = AnimPayload(0f, AnimGroups.MAIN) }
            val jumpLand = state("jump_land")

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
            s.playRelativeAnim(animName)
        }
    }

    private var lastAnim: AnimInstance? = null

    private fun IState.playRelativeAnim(animName: String) {
        val payload = payload
        val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.PlayerState(player, animInstance(player, animName, false), this))
        if (event.isCanceled) return
        val anim = (event.newAnim ?: event.originAnim) ?: return
        anim.apply {
            if (payload is AnimPayload) {
                group = payload.group
                inTransitionTime = payload.transTime
            } else {
                group = AnimGroups.STATE
            }
            lastAnim?.let { if (it.origin.loop != Loop.ONCE) it.exit() }
            enter()
        }
        lastAnim = anim
    }

    private suspend fun IState.setupMovementStates() {
        val idle = state("$name.idle")
        val move = state("$name.move")
        val moveBack = state("$name.move_back")
        val sprint = state("$name.sprint")

        initialChoiceState {
            when {
                player.isSprinting -> sprint
                player.moveBackCheck() -> moveBack
                player.moveCheck() -> move
                else -> idle
            }
        }
    }

    override fun progress() {
        machine.processEventBlocking(SwitchEvent)
    }

    object IdleEvent: Event
    object MoveEvent: Event
    object MoveBackEvent: Event
    object SprintEvent: Event

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