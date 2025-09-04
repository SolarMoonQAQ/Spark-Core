package cn.solarmoon.spark_core.lua.doc

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import java.nio.file.Paths

object LuaDocsGenerator {

    @JvmStatic
    fun eval() {
        val sourceDir = Paths.get("src/main/kotlin").toFile() // 扫描整个项目源码
        val outputDir = Paths.get("src/main/resources/spark_modules/docs").toFile()
        outputDir.mkdirs()

        val disposable = Disposer.newDisposable()
        val configuration = CompilerConfiguration()
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "parser")
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        configuration.addJvmClasspathRoots(getCompileClasspath())

        val env = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        val psiManager = PsiManager.getInstance(env.project)

        sourceDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val vFile = CoreLocalFileSystem().refreshAndFindFileByPath(file.path)
            if (vFile != null) {
                val ktFile = psiManager.findFile(vFile) as? KtFile ?: return@forEach
                processKtFile(ktFile, outputDir)
            }
        }

        Disposer.dispose(disposable)
    }

    private fun processKtFile(ktFile: KtFile, outputDir: File) {
        ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { clazz ->
            val hasLuaClass = clazz.annotationEntries.any { it.shortName?.asString() == "LuaClass" }
            val hasLuaGlobal = clazz.annotationEntries.any { it.shortName?.asString() == "LuaGlobal" }

            if (!hasLuaClass && !hasLuaGlobal) return@forEach

            val originalName = clazz.name ?: return@forEach

            // 文件名：去掉 Lua 前缀，但保留 Global 后缀
            val fileName = originalName.removePrefix("Lua")

            // Lua 内部类名：去掉 Lua 前缀和 Global 后缀
            val className = originalName
                .removePrefix("Lua")
                .removeSuffix("Global")

            val output = StringBuilder()

            if (hasLuaClass) {
                output.appendLine("---@class $className")
                clazz.docComment?.getDefaultSection()?.getContent()?.trim()?.let {
                    if (it.isNotEmpty()) output.appendLine("---$it")
                }
                output.appendLine("local $className = {}")
                output.appendLine("$className.__index = $className")
                output.appendLine()

                clazz.declarations.filterIsInstance<KtNamedFunction>().forEach { func ->
                    appendFunctionDoc(output, func, "$className:${func.name}")
                }

            } else if (hasLuaGlobal) {
                clazz.declarations.filterIsInstance<KtNamedFunction>().forEach { func ->
                    appendFunctionDoc(output, func, "$className.${func.name}")
                }
            }

            // 文件名用 fileName，避免覆盖
            File(outputDir, "$fileName.lua").writeText(output.toString())
        }
    }



    // 公共方法：生成函数的参数、返回值、说明
    private fun appendFunctionDoc(output: StringBuilder, func: KtNamedFunction, funcSignature: String) {
        val kdoc = func.docComment
        val paramTags = kdoc?.findTagsByName("param") ?: emptyList()
        val returnTags = kdoc?.findTagsByName("return") ?: emptyList()

        // 参数
        func.valueParameters.forEach { param ->
            val type = javaTypeToLua(param.typeReference?.text ?: "any")
            val tag = paramTags.firstOrNull { it.getSubjectName() == param.name }
            val desc = tag?.getContent()?.trim()?.lines()?.joinToString("\n---") ?: ""
            if (desc.isNotEmpty()) {
                output.appendLine("---@param ${param.name} $type $desc")
            } else {
                output.appendLine("---@param ${param.name} $type")
            }
        }

        // 返回值
        val returnType = javaTypeToLua(func.typeReference?.text ?: "void")
        if (returnType != "void") {
            val tag = returnTags.firstOrNull()
            val desc = tag?.getContent()?.trim()?.lines()?.joinToString("\n---") ?: ""
            if (desc.isNotEmpty()) {
                output.appendLine("---@return $returnType $desc")
            } else {
                output.appendLine("---@return $returnType")
            }
        }

        // 方法整体说明
        val funcDescRaw = kdoc?.getDefaultSection()?.getContent()?.trim()
        if (!funcDescRaw.isNullOrEmpty()) {
            funcDescRaw.lines().forEach { line ->
                val clean = line.trim()
                if (clean.isNotEmpty()) {
                    output.appendLine("---$clean")
                }
            }
        }

        // 方法定义
        val paramsList = func.valueParameters.joinToString(", ") { it.name ?: "" }
        output.appendLine("function $funcSignature($paramsList)")
        output.appendLine("end")
        output.appendLine()
    }


    fun KDoc.findTagsByName(name: String): List<KDocTag> {
        return this.getAllSections()
            .flatMap { it.findTagsByName(name) }
    }

    private fun javaTypeToLua(javaType: String): String {
        return when (javaType.removeSuffix("?")) {
            "String" -> "string"
            "Boolean", "boolean" -> "boolean"
            "Int", "Long", "Float", "Double",
            "int", "long", "float", "double" -> "number"
            "Unit", "void" -> "void"
            else -> javaType
        }
    }

    private fun getCompileClasspath(): List<File> {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparator).map { File(it) }.filter { it.exists() }
    }
    
}
