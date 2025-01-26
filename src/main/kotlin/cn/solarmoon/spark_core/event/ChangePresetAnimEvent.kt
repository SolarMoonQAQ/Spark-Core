package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.preset_anim.CommonState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import ru.nsk.kstatemachine.state.IState

abstract class ChangePresetAnimEvent: Event() {

    class PlayerState(val player: Player, val originAnim: TypedAnimation, val state: IState, var transitionTime: Int): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: TypedAnimation? = null
    }

    class Common(val entity: Entity, val originAnim: String, val commonState: CommonState): ChangePresetAnimEvent() {
        var newAnim: String? = null
    }

}