package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.api.ParticleEffects
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.molang.evalAsDouble
import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import java.util.Optional
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * 进入状态时触发粒子效果。
 *
 * 当前仅支持 [IEntityAnimatable] 持有者。
 * - 若 [locator] 非 null：绑定到对应 locator（需实体实现相关接口）
 * - 否则：在实体位置触发
 */
class ParticleAction(
    val effect: String,
    val locator: String? = null,
    val bindToActor: Boolean = false,
    val preEffectScript: JSMolangValue? = null
) : StateAction {

    override val codec = CODEC

    override fun execute(controller: StateGraphController) {
        val ctrl = controller as? AnimStateMachine ?: run {
            SparkCore.LOGGER.warn("ParticleAction 只能在 AnimStateMachine 上下文中执行，当前控制器为 {}", controller::class.simpleName)
            return
        }
        val entityAnimatable = ctrl.animatable as? IEntityAnimatable<*>
        if (entityAnimatable == null) {
            SparkCore.LOGGER.warn("ParticleAction 仅支持 IEntityAnimatable 持有者")
            return
        }
        val level = entityAnimatable.animLevel
        val entity = entityAnimatable.animatable

        preEffectScript?.evalAsDouble(ctrl.animatable)

        val effectId = try {
            ResourceLocation.parse(effect)
        } catch (e: Exception) {
            SparkCore.LOGGER.warn("非法的粒子效果 ID [{}]", effect)
            return
        }

        try {
            if (locator != null) {
                ParticleEffects.burst(level, effectId, locator, entity.uuid)
            } else {
                val pos = Vec3(entity.x, entity.y, entity.z)
                ParticleEffects.burst(level, effectId, pos, Quaternionf())
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.warn("无法触发粒子效果 [{}]: {}", effect, e.message)
        }
    }

    companion object {
        val CODEC: MapCodec<ParticleAction> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.fieldOf("effect").forGetter(ParticleAction::effect),
                Codec.STRING.optionalFieldOf("locator").forGetter { Optional.ofNullable(it.locator) },
                Codec.BOOL.optionalFieldOf("bind_to_actor", false).forGetter(ParticleAction::bindToActor),
                JSMolangValue.CODEC.optionalFieldOf("pre_effect_script").forGetter { Optional.ofNullable(it.preEffectScript) }
            ).apply(it) { effect, locator, bindToActor, preEffectScript ->
                ParticleAction(effect, locator.orElse(null), bindToActor, preEffectScript.orElse(null))
            }
        }
    }

}
