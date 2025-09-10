package cn.solarmoon.spark_core.js.doc

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
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File
import java.nio.file.Paths

object TSDocsGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        eval()
    }

    fun eval() {
        val sourceDir = Paths.get("src/main/kotlin").toFile() // 扫描整个项目源码
        val outputDir = Paths.get("src/main/resources/spark_modules/.docs").toFile()
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
        ktFile.collectDescendantsOfType<KtClassOrObject>().forEach { clazz ->
            val jsClassAnn = clazz.annotationEntries.firstOrNull { it.shortName?.asString() == "JSClass" }
            val jsGlobalAnn = clazz.annotationEntries.firstOrNull { it.shortName?.asString() == "JSGlobal" }
            if (jsClassAnn == null && jsGlobalAnn == null) return@forEach

            val tsName = (jsClassAnn ?: jsGlobalAnn)
                ?.valueArguments
                ?.firstOrNull()
                ?.getArgumentExpression()
                ?.text
                ?.trim('"') ?: return@forEach

            val output = StringBuilder()

            // 类的 KDoc
            appendKDoc(output, clazz.docComment, 0)

            if (jsClassAnn != null) {
                output.appendLine("interface $tsName {")
                clazz.primaryConstructorParameters
                    .filter { it.hasValOrVar() }
                    .forEach { param ->
                        val propName = param.name ?: return@forEach
                        val tsType = javaTypeToTs(param.typeReference?.text ?: "any")

                        // 属性的 KDoc
                        appendKDoc(output, param.docComment, 1)

                        output.appendLine("    $propName: $tsType;")
                    }

                clazz.declarations.filterIsInstance<KtNamedFunction>().forEach { func ->
                    var funcName = func.name ?: return@forEach
                    if (funcName.startsWith("js_")) {
                        funcName = funcName.removePrefix("js_")
                    }

                    val params = func.valueParameters.joinToString(", ") { p ->
                        "${p.name}: ${javaTypeToTs(p.typeReference?.text ?: "any")}"
                    }
                    val returnType = javaTypeToTs(func.typeReference?.text ?: "void")

                    appendKDoc(output, func.docComment, 1)
                    output.appendLine("    $funcName($params): $returnType;")
                }

                output.appendLine("}")
                File(outputDir, "$tsName.d.ts").writeText(output.toString())
            }
            else if (jsGlobalAnn != null) {
                output.appendLine("declare namespace $tsName {")
                clazz.declarations.filterIsInstance<KtNamedFunction>().forEach { func ->
                    var funcName = func.name ?: return@forEach
                    if (funcName.startsWith("js_")) {
                        funcName = funcName.removePrefix("js_")
                    }

                    val params = func.valueParameters.joinToString(", ") { p ->
                        "${p.name}: ${javaTypeToTs(p.typeReference?.text ?: "any")}"
                    }
                    val returnType = javaTypeToTs(func.typeReference?.text ?: "void")

                    appendKDoc(output, func.docComment, 1)
                    output.appendLine("    function $funcName($params): $returnType;")
                }
                output.appendLine("}")
                File(outputDir, "${tsName}Global.d.ts").writeText(output.toString())
            }
        }
    }


    // ===== 把 KDoc 转成 TypeScript 注释 =====
    private fun appendKDoc(sb: StringBuilder, kdoc: KDoc?, indentLevel: Int) {
        if (kdoc == null) return
        val indent = "    ".repeat(indentLevel)
        sb.appendLine("${indent}/**")

        val section = kdoc.getDefaultSection()

        // 主体内容
        section.getContent().trim().lines().forEach { line ->
            sb.appendLine("$indent * ${line.trim()}")
        }

        // 标签内容（注意这里用 children 过滤）
        section.children.filterIsInstance<KDocTag>().forEach { tag ->
            val tagName = tag.name // "param", "return" 等
            val subjectName = tag.getSubjectName() // 对于 @param 是参数名
            val content = tag.getContent().trim()
            if (subjectName != null) {
                sb.appendLine("$indent * @$tagName $subjectName $content")
            } else {
                sb.appendLine("$indent * @$tagName $content")
            }
        }

        sb.appendLine("${indent} */")
    }

    // 1) 类型映射：覆盖 List、函数类型、通配符、基础类型、保留自定义类型名
    private fun javaTypeToTs(javaType: String): String {
        var t = javaType.trim().removeSuffix("?")

        // 替换通配符 <*> → <any>
        t = t.replace(Regex("<\\s*\\*>"), "<any>")

        // List<T> → T[]
        val list = Regex("""(?:kotlin\.)?List<(.+)>""")
        list.matchEntire(t)?.let { m ->
            val inner = m.groupValues[1].trim()
            return "${javaTypeToTs(inner)}[]"
        }

        // 函数类型 1： (A, B) -> R
        val arrowFn = Regex("""^\((.*)\)\s*->\s*(.+)$""")
        arrowFn.matchEntire(t)?.let { m ->
            val paramsRaw = m.groupValues[1].trim()
            val retRaw = m.groupValues[2].trim()
            val params = if (paramsRaw.isEmpty()) "" else {
                paramsRaw.split(",").mapIndexed { i, p ->
                    val part = p.trim()
                    // 允许 "name: Type" 或仅 "Type"
                    val colon = part.indexOf(':')
                    val (name, type) = if (colon >= 0) {
                        part.substring(0, colon).trim() to part.substring(colon + 1).trim()
                    } else {
                        "arg$i" to part
                    }
                    "$name: ${javaTypeToTs(type)}"
                }.joinToString(", ")
            }
            val ret = javaTypeToTs(retRaw)
            return "($params) => $ret"
        }

        // 函数类型 2： FunctionN<A, B, R>
        val fnN = Regex("""^(?:kotlin\.)?Function(\d+)<(.+)>$""")
        fnN.matchEntire(t)?.let { m ->
            val args = m.groupValues[2].split(',').map { it.trim() }
            if (args.isNotEmpty()) {
                val ret = javaTypeToTs(args.last())
                val params = args.dropLast(1).mapIndexed { i, a -> "arg$i: ${javaTypeToTs(a)}" }.joinToString(", ")
                return "($params) => $ret"
            }
        }

        // 基础类型
        return when (t) {
            "String" -> "string"
            "Boolean", "boolean" -> "boolean"
            "Int", "Long", "Float", "Double", "Short", "Byte",
            "int", "long", "float", "double", "short", "byte" -> "number"
            "Unit", "void", "kotlin.Unit" -> "void"
            "BooleanArray" -> "boolean[]"
            "Any", "kotlin.Any" -> "any"
            else -> simplifyQualifiedName(t) // 保留类型名
        }
    }

    // 把全限定名简化为短名
    private fun simplifyQualifiedName(name: String): String {
        // 例如 "cn.solarmoon.spark_core.skill.SkillType<cn...Skill>" → "SkillType<Skill>"
        return name.replace(Regex("""([a-zA-Z_][\w\.]*\.)+([A-Z]\w*)""")) { m ->
            m.groupValues.last()
        }
    }





    private fun getCompileClasspath(): List<File> {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparator).map { File(it) }.filter { it.exists() }
    }
    
}
