package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.presets.CommonState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import ru.nsk.kstatemachine.state.IState

abstract class ChangePresetAnimEvent: Event() {

    class PlayerState(val player: Player, val originAnim: TypedAnimation, val state: IState, var transitionTime: Int): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: TypedAnimation? = null
    }

    class EntityState(val animatable: IEntityAnimatable<out LivingEntity>, val originAnim: TypedAnimation, val state: IState, var transitionTime: Int, val originFromAnimatable: Boolean): ChangePresetAnimEvent(), ICancellableEvent {
        var newAnim: TypedAnimation? = null
            set(value) {
                if (!fromAnimatable && value?.exist() != true) return
                if (fromAnimatable && value?.exist(animatable) != true) return
                field = value
            }
        var fromAnimatable = originFromAnimatable

        val entity get() = animatable.animatable
    }

    class Common(val entity: Entity, val originAnim: String, val commonState: CommonState): ChangePresetAnimEvent() {
        var newAnim: String? = null
    }

}