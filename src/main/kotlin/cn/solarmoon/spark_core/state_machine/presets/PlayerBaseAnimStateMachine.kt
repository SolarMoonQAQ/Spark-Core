package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.registry.common.SparkStateMachineRegister
import cn.solarmoon.spark_core.state_machine.StateMachineHandler
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.LadderBlock
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
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
        initialState("233")
    }

    override fun progress() {
        machine?.processEventBlocking(SwitchEvent)
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