package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.state_machine.presets.AnimPlayDataProvider
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import ru.nsk.kstatemachine.state.IState

abstract class ChangePresetAnimEvent: Event() {

    class PlayerState(
        val player: Player,
        val originAnim: AnimInstance,
        val state: IState,
        var data: AnimPlayDataProvider
    ): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: AnimInstance? = null
    }

    class EntityUseState(
        val entity: LivingEntity,
        val originAnim: AnimInstance,
        val state: IState,
        var data: AnimPlayDataProvider
    ): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: AnimInstance? = null
    }

}