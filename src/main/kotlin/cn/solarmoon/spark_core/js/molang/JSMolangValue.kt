package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.event.OnMolangValueBindingEvent
import cn.solarmoon.spark_core.js.eval
import cn.solarmoon.spark_core.js.getJSBindings
import cn.solarmoon.spark_core.js.safeGetOrCreateJSContext
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import org.graalvm.polyglot.Value

@JvmInline
value class JSMolangValue(val value: String) {

    fun eval(animatable: IAnimatable<*>): Value {
        val context = getMolangJSContext()
        context.getJSBindings().apply {
            putMember("q", QueryContext(animatable))
            putMember("query", QueryContext(animatable))
            NeoForge.EVENT_BUS.post(OnMolangValueBindingEvent(this@JSMolangValue, animatable, context, this))
        }
        return context.eval(value)
    }

    override fun toString() = value

    companion object {
        val CODEC: Codec<JSMolangValue> = Codec.either(Codec.STRING, Codec.FLOAT).xmap(
            { either -> either.map({ JSMolangValue(it) }, { JSMolangValue(it.toString()) }) },
            { str ->
                // 这里简单处理：如果能转成 Double 就走右分支，否则走左分支
                str.toString().toFloatOrNull()?.let { Either.right(it) } ?: Either.left(str.toString())
            }
        )
    }

}