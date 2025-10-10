package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.anim.AnimInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import ru.nsk.kstatemachine.state.IState

abstract class ChangePresetAnimEvent: Event() {

    class PlayerState(
        val player: Player,
        val originAnim: AnimInstance?,
        val state: IState
    ): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: AnimInstance? = originAnim
    }

    class EntityUseState(
        val entity: LivingEntity,
        val originAnim: AnimInstance?,
        val state: IState
    ): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: AnimInstance? = originAnim
    }

}