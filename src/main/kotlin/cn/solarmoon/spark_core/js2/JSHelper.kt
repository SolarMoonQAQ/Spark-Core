package cn.solarmoon.spark_core.js2

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.graalvm.polyglot.Value

fun Value.safeGetMember(name: String) = if (hasMember(name)) getMember(name) else null

fun Value.toArray() = (0 until arraySize).map { getArrayElement(it) }.toTypedArray()

inline fun <reified T> Value.toArray(transform: (Value) -> T) = (0 until arraySize).map { transform(getArrayElement(it)) }.toTypedArray()

fun Value.toVec3() = Vec3(getArrayElement(0).asDouble(), getArrayElement(1).asDouble(), getArrayElement(2).asDouble())

fun Value.toVec2() = Vec2(getArrayElement(0).asFloat(), getArrayElement(1).asFloat())

fun Vec3.toValue() = SparkJSLoader.get().context.asValue(arrayOf(x, y, z))

fun Vec2.toValue() = SparkJSLoader.get().context.asValue(arrayOf(x, y))