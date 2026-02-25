package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.model.ModelIndex
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Value
import java.util.concurrent.ConcurrentHashMap

@JvmInline
value class JSMolangValue(val value: String) {

    val context get() = getMolangJSContext()

    fun eval(anim: AnimInstance): Value {
        val src = MolangCache.getOrCompile(value)
        val context = getMolangJSContext()
        val bindings = context.getJSBindings()
        for (extra in getMolangExtraBindings().values) { // 更新额外绑定的上下文
            extra.update(value, anim, context, bindings)
        }
        return context.eval(src) // 执行表达式并返回结果
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

/** 空 AnimIndex，用于在无动画的情况下解析Molang表达式 */
private val animIndex = AnimIndex(ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty")), "empty")


private object MolangConstantCache {
    private val cache = ConcurrentHashMap<String, Double?>()

    operator fun get(expr: String): Double? =
        cache.computeIfAbsent(expr) { it.toDoubleOrNull() }
}

// 返回 Double 的扩展函数 - 添加布尔值转换逻辑
@JvmName("evalAsDouble")
fun JSMolangValue.evalAsDouble(anim: AnimInstance): Double {
    MolangConstantCache[value]?.let { return it }
    val value = this.eval(anim)
    return when {
        value.isBoolean -> if (value.asBoolean()) 1.0 else 0.0
        value.fitsInDouble() -> value.asDouble()
        else -> 0.0
    }
}

@JvmName("evalAsDouble")
fun JSMolangValue.evalAsDouble(animatable: IAnimatable<*>): Double {
    MolangConstantCache[value]?.let { return it }
    val value = this.eval(AnimInstance(animatable, animIndex))
    return when {
        value.isBoolean -> if (value.asBoolean()) 1.0 else 0.0
        value.fitsInDouble() -> value.asDouble()
        else -> 0.0
    }
}

// 返回 Boolean 的扩展函数
@JvmName("evalAsBoolean")
fun JSMolangValue.evalAsBoolean(anim: AnimInstance): Boolean {
    MolangConstantCache[value]?.let { return it > 0.0 }
    val value = this.eval(anim)
    return value.isBoolean && value.asBoolean()
}

@JvmName("evalAsBoolean")
fun JSMolangValue.evalAsBoolean(animatable: IAnimatable<*>): Boolean {
    MolangConstantCache[value]?.let { return it > 0.0 }
    val value = this.eval(AnimInstance(animatable, animIndex))
    return value.isBoolean && value.asBoolean()
}

// 返回 String 的扩展函数
@JvmName("evalAsString")
fun JSMolangValue.evalAsString(anim: AnimInstance): String {
    MolangConstantCache[value]?.let { return it.toString() }
    val value = this.eval(anim)
    return when {
        value.isString -> value.asString()
        else -> value.toString()
    }
}

@JvmName("evalAsString")
fun JSMolangValue.evalAsString(animatable: IAnimatable<*>): String {
    MolangConstantCache[value]?.let { return it.toString() }
    val animInstance = AnimInstance(animatable, animIndex)
    val value = this.eval(animInstance)
    return when {
        value.isString -> value.asString()
        else -> value.toString()
    }
}

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
    val animInstance = AnimInstance(animatable, animIndex)
    val value = this.eval(animInstance)
    return when {
        value.isBoolean -> value.asBoolean()
        value.isString -> value.asString()
        value.fitsInDouble() -> value.asDouble()
        else -> value.toString() // 兜底处理
    }
}