package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.molang.MolangContextRegistry
import cn.solarmoon.spark_core.molang.SparkMolangContext
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

@JvmInline
value class JSMolangValue(val value: String) {

    override fun toString() = value

    companion object {
        val CODEC: Codec<JSMolangValue> = Codec.either(Codec.STRING, Codec.FLOAT).xmap(
            { either -> either.map({ JSMolangValue(it) }, { JSMolangValue(it.toString()) }) },
            { str ->
                str.toString().toFloatOrNull()?.let { Either.right(it) } ?: Either.left(str.toString())
            }
        )

        val STREAM_CODEC = ByteBufCodecs.BYTE_ARRAY.map({ JSMolangValue(it.toString()) }, { it.value.toByteArray() })
    }

}

// ========== 缓存与工具 ==========

/** 空 AnimIndex，用于在无动画的情况下解析Molang表达式 */
private val animIndex = AnimIndex(ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty")), "empty")


/** 纯数字常量缓存，跳过编译流程 */
private object MolangConstantCache {
    private val cache = ConcurrentHashMap<String, Double?>()

    operator fun get(expr: String): Double? =
        cache.computeIfAbsent(expr) { it.toDoubleOrNull() }
}

/** 从 AnimInstance 获取 IAnimatable 的上下文并设置动画时间 */
@Suppress("UNCHECKED_CAST")
private fun AnimInstance.toSparkContext(): SparkMolangContext<*> {
    val ctx = holder.getMolangContext()
    val animTime = time.toDouble() / tps.toDouble()
    (ctx as SparkMolangContext<IAnimatable<*>>).reset(holder, animTime)
    return ctx
}

/** 从 IAnimatable 获取上下文 */
private fun IAnimatable<*>.toSparkContext(): SparkMolangContext<*> {
    return getMolangContext()
}

// ========== 求值扩展函数 ==========

// 返回 Double 的扩展函数
@JvmName("evalAsDouble")
fun JSMolangValue.evalAsDouble(anim: AnimInstance): Double {
    MolangConstantCache[value]?.let { return it }
    val ctx = anim.toSparkContext()
    return MolangContextRegistry.compile(value, ctx).evaluate(ctx)
}

@JvmName("evalAsDouble")
fun JSMolangValue.evalAsDouble(animatable: IAnimatable<*>): Double {
    MolangConstantCache[value]?.let { return it }
    val ctx = animatable.toSparkContext()
    return MolangContextRegistry.compile(value, ctx).evaluate(ctx)
}

// 返回 Boolean 的扩展函数
@JvmName("evalAsBoolean")
fun JSMolangValue.evalAsBoolean(anim: AnimInstance): Boolean {
    MolangConstantCache[value]?.let { return it > 0.0 }
    return evalAsDouble(anim) > 0.0
}

@JvmName("evalAsBoolean")
fun JSMolangValue.evalAsBoolean(animatable: IAnimatable<*>): Boolean {
    MolangConstantCache[value]?.let { return it > 0.0 }
    return evalAsDouble(animatable) > 0.0
}

// 返回 String 的扩展函数 — 通过 StringExpression 通道支持字符串值
@JvmName("evalAsString")
fun JSMolangValue.evalAsString(anim: AnimInstance): String {
    MolangConstantCache[value]?.let { return it.toString() }
    val ctx = anim.toSparkContext()
    return MolangContextRegistry.compileString(value, ctx).evaluate(ctx)
}

@JvmName("evalAsString")
fun JSMolangValue.evalAsString(animatable: IAnimatable<*>): String {
    MolangConstantCache[value]?.let { return it.toString() }
    val ctx = animatable.toSparkContext()
    return MolangContextRegistry.compileString(value, ctx).evaluate(ctx)
}

@JvmName("eval")
/**
 * 通用方法：返回多态对象（String / Double）。
 * 走 StringExpression 通道，数值字符串解析回 Double。
 */
fun JSMolangValue.evalAsObject(anim: AnimInstance): Any? {
    val ctx = anim.toSparkContext()
    return MolangContextRegistry.evalAsObject(value, ctx)
}

@JvmName("eval")
/**
 * 通用方法：返回多态对象（String / Double）。
 */
fun JSMolangValue.evalAsObject(animatable: IAnimatable<*>): Any {
    val ctx = animatable.toSparkContext()
    return MolangContextRegistry.evalAsObject(value, ctx)
}
