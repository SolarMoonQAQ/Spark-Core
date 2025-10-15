package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.js.molang.JSMolangValue
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class OParticleEffect(
    val effect: String?,
    val locator: String?,
    val bindToActor: Boolean,
    val preEffectScript: JSMolangValue
) {
    companion object {
        val CODEC: Codec<OParticleEffect> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.STRING.optionalFieldOf("effect").forGetter { Optional.ofNullable(it.effect) },
                Codec.STRING.optionalFieldOf("locator").forGetter { Optional.ofNullable(it.locator) },
                Codec.BOOL.optionalFieldOf("bind_to_actor", false).forGetter { it.bindToActor },
                JSMolangValue.CODEC.optionalFieldOf("pre_effect_script", JSMolangValue("0")).forGetter { it.preEffectScript }
            ).apply(ins) { a, b, c, d -> OParticleEffect(a.getOrNull(), b.getOrNull(), c, d) }
        }
    }
}