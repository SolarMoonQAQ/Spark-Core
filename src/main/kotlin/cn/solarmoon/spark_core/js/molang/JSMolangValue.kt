package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.event.OnMolangValueBindingEvent
import cn.solarmoon.spark_core.js.eval
import cn.solarmoon.spark_core.js.getJSBindings
import cn.solarmoon.spark_core.js.safeGetOrCreateJSContext
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject

@JvmInline
value class JSMolangValue(val value: String) {

    val context get() = getMolangJSContext()

    fun eval(anim: AnimInstance): Value {
        context.getJSBindings().apply {
            putMember("math", MathContext())
            putMember("q", QueryContext(anim))
            putMember("query", QueryContext(anim))
            NeoForge.EVENT_BUS.post(OnMolangValueBindingEvent(this@JSMolangValue, anim, context, this))
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

        val STREAM_CODEC = ByteBufCodecs.BYTE_ARRAY.map({ JSMolangValue(it.toString()) }, { it.value.toByteArray() })
    }

}

// ========== 扩展函数 ==========

// 返回 Double 的扩展函数 - 添加布尔值转换逻辑
@JvmName("evalAsDouble")
fun JSMolangValue.evalAsDouble(anim: AnimInstance): Double {
    val value = this.eval(anim)
    return when {
        value.isBoolean -> if (value.asBoolean()) 1.0 else 0.0
        value.fitsInDouble() -> value.asDouble()
        else -> 0.0
    }
}

@JvmName("evalAsDouble")
fun JSMolangValue.evalAsDouble(animatable: IAnimatable<*>): Double {
    val value = this.eval(
        AnimInstance(
            animatable,
            AnimIndex(ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty")), "empty")
        )
    )
    return when {
        value.isBoolean -> if (value.asBoolean()) 1.0 else 0.0
        value.fitsInDouble() -> value.asDouble()
        else -> 0.0
    }
}
// 返回 Boolean 的扩展函数
@JvmName("evalAsBoolean")
fun JSMolangValue.evalAsBoolean(anim: AnimInstance): Boolean =
    this.eval(anim).takeIf { it.isBoolean }?.asBoolean() == false

@JvmName("evalAsBoolean")
fun JSMolangValue.evalAsBoolean(animatable: IAnimatable<*>): Boolean =
    this.eval(
        AnimInstance(
            animatable,
            AnimIndex(ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty")), "empty")
        )
    ).takeIf { it.isBoolean }?.asBoolean() == false

// 返回 String 的扩展函数
@JvmName("evalAsString")
fun JSMolangValue.evalAsString(anim: AnimInstance): String =
    this.eval(anim).takeIf { it.isString }?.asString() ?: this.eval(anim).toString()

@JvmName("evalAsString")
fun JSMolangValue.evalAsString(animatable: IAnimatable<*>): String =
    this.eval(
        AnimInstance(
            animatable,
            AnimIndex(ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty")), "empty")
        )
    ).takeIf { it.isString }?.asString() ?: this.eval(
        AnimInstance(
            animatable,
            AnimIndex(ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty")), "empty")
        )
    ).toString()

@JvmName("eval")
/**
 * 通用方法：返回 Object (Boolean/Double/String)
 */
fun JSMolangValue.evalAsObject(anim: AnimInstance): Any? {
    val value = this.eval(anim)
    return when {
        value.isNull -> null
        value.isBoolean -> value.asBoolean()
        value.isString -> value.asString()
        value.fitsInDouble() -> value.asDouble()
        else -> value.toString() // 兜底处理
    }
}

@JvmName("eval")
/**
 * 通用方法：返回 Object (Boolean/Double/String)
 */
fun JSMolangValue.evalAsObject(animatable: IAnimatable<*>): Any {
    val animInstance = AnimInstance(
        animatable,
        AnimIndex(ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty")), "empty")
    )
    val value = this.eval(animInstance)
    return when {
        value.isBoolean -> value.asBoolean()
        value.isString -> value.asString()
        value.fitsInDouble() -> value.asDouble()
        else -> value.toString() // 兜底处理
    }
}