package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * 进入状态时播放音效。
 *
 * 当前仅支持 [IEntityAnimatable] 持有者，在实体位置播放一次音效。
 * 非实体上下文将输出警告并跳过。
 */
class SoundAction(
    val soundName: String
) : StateAction {

    override val codec = CODEC

    override fun execute(controller: StateGraphController) {
        val ctrl = controller as? AnimStateMachine ?: run {
            SparkCore.LOGGER.warn("SoundAction 只能在 AnimStateMachine 上下文中执行，当前控制器为 {}", controller::class.simpleName)
            return
        }
        val entityAnimatable = ctrl.animatable as? IEntityAnimatable<*>
        if (entityAnimatable == null) {
            SparkCore.LOGGER.warn("SoundAction 仅支持 IEntityAnimatable 持有者")
            return
        }
        val level = entityAnimatable.animLevel
        val entity = entityAnimatable.animatable
        try {
            val soundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(soundName))
            level.playSound(null, entity.x, entity.y, entity.z, soundEvent, SoundSource.NEUTRAL, 1.0f, 1.0f)
        } catch (e: Exception) {
            SparkCore.LOGGER.warn("无法播放音效 [{}]: {}", soundName, e.message)
        }
    }

    companion object {
        val CODEC: MapCodec<SoundAction> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.fieldOf("sound").forGetter(SoundAction::soundName)
            ).apply(it, ::SoundAction)
        }
    }

}
