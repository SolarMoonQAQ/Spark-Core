package cn.solarmoon.spark_core.js

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject


// loadDefaultScripts函数已废弃，功能已迁移到DynamicJavaScriptHandler.initializeDefaultResources()
// 使用统一的ResourceExtractionUtil工具进行资源提取

fun Function.call(js: SparkJS, vararg args: Any?) = js.withContext { context ->
    call(context, js.scope, js.scope, args)
}

fun Scriptable.getMember(name: String) = ScriptableObject.getProperty(this, name) as? Scriptable

fun Scriptable.getFunctionMember(name: String) = getMember(name) as? Function

fun ScriptableObject.put(name: String, value: Any) = put(name, this, Context.javaToJS(value, this))

fun NativeArray.toVec3() = Vec3(Context.toNumber(get(0)), Context.toNumber(get(1)), Context.toNumber(get(2)))

fun Vec3.toNativeArray() = NativeArray(arrayOf(x, y, z))

fun NativeArray.toVec2() = Vec2(Context.toNumber(get(0)).toFloat(), Context.toNumber(get(1)).toFloat())

fun Vec2.toNativeArray() = NativeArray(arrayOf(x, y))