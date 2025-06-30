package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.state.AnimPlayDataProvider
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import ru.nsk.kstatemachine.state.IState

abstract class ChangePresetAnimEvent: Event() {

    class PlayerState(
        val player: Player,
        val originAnim: TypedAnimation,
        val state: IState,
        var data: AnimPlayDataProvider
    ): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: TypedAnimation? = null
    }

    class EntityUseState(
        val entity: LivingEntity,
        val originAnim: TypedAnimation,
        val state: IState,
        var data: AnimPlayDataProvider
    ): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: TypedAnimation? = null
    }

}