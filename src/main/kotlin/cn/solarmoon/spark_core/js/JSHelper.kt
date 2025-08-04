package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import net.neoforged.fml.loading.FMLPaths
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.reflect.KClass


// loadDefaultScripts函数已废弃，功能已迁移到DynamicJavaScriptHandler.initializeDefaultResources()
// 使用统一的ResourceExtractionUtil工具进行资源提取

fun Function.call(js: SparkJS, vararg args: Any?) = js.withContext { context ->
    call(context, js.scope, js.scope, args)
}

fun Scriptable.getMember(name: String) = ScriptableObject.getProperty(this, name) as? Scriptable

fun Scriptable.getFunctionMember(name: String) = getMember(name) as? Function

fun ScriptableObject.put(name: String, value: Any) = put(name, this, Context.javaToJS(value, this))