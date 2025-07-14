package cn.solarmoon.spark_core.resource.packaging

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ModuleGraphManager
import cn.solarmoon.spark_core.resource.origin.OModuleInfo
import cn.solarmoon.spark_core.resource.origin.OResourceDependency
import cn.solarmoon.spark_core.resource.origin.PackagingScope

import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 资源打包工具 (重构后)
 *
 * 实现了依赖烘焙和基于打包范围的精细化打包逻辑。
 */
object PackagingTool {
    
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 烘焙并打包一个新的模块。
     *
     * @param newModuleId 新模块的ID。
     * @param newModuleVersion 新模块的版本。
     * @param selectedResources 用户选择的要直接包含在包中的资源列表。
     * @param dependencyScopes 一个映射，用于为特定依赖指定打包范围（覆盖默认行为）。
     * @param outputPath 输出的.spark文件路径。
     * @return 打包操作的结果。
     */
    fun bakeAndPackage(
        newModuleId: String,
        newModuleVersion: String,
        selectedResources: List<ResourceLocation>,
        dependencyScopes: Map<ResourceLocation, PackagingScope> = emptyMap(),
        outputPath: Path
    ): PackagingResult {
        val tempDir: Path
        try {
            // 1. 创建临时打包目录
            tempDir = Files.createTempDirectory("spark_packaging_")
            SparkCore.LOGGER.info("创建临时打包目录: $tempDir")

            // 2. 收集所有需要处理的资源
            val resourcesToProcess = mutableSetOf<ResourceLocation>()
            selectedResources.forEach { startRes ->
                resourcesToProcess.add(startRes)
                val allDeps = ResourceGraphManager.getAllDependencies(startRes, hardOnly = true) // 只追踪硬依赖
                resourcesToProcess.addAll(allDeps.map { it.id })
            }

            // 3. 遍历资源，根据打包范围决策
            val includedFiles = mutableSetOf<Path>()
            val referencedDependencies = mutableListOf<OResourceDependency>()
            val includedModules = mutableSetOf<String>()

            for (resId in resourcesToProcess) {
                val scope = dependencyScopes[resId] ?: PackagingScope.INCLUDE // 默认包含硬依赖

                if (scope == PackagingScope.INCLUDE) {
                    val node = ResourceGraphManager.getResourceNode(resId)
                    if (node == null) {
                        SparkCore.LOGGER.warn("无法找到资源 $resId 的节点信息，跳过打包。")
                        continue
                    }

                    // 当前，我们直接打包整个文件。
                    val sourceFile = node.basePath.resolve(node.relativePath)
                    if (Files.exists(sourceFile) && !includedFiles.contains(sourceFile)) {
                        val targetFile = tempDir.resolve(node.relativePath)
                        Files.createDirectories(targetFile.parent)
                        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
                        includedFiles.add(sourceFile)

                        // 记录包含的模块
                        includedModules.add(node.namespace)
                        SparkCore.LOGGER.debug("包含资源文件: {} -> {}", resId, targetFile)
                    }
                } else { // REFERENCE_ONLY
                    val node = ResourceGraphManager.getResourceNode(resId)
                    if (node != null) {
                        // 收集引用依赖信息
                        val deps = ResourceGraphManager.getDirectDependencies(resId)
                        referencedDependencies.addAll(deps)

                        // 记录引用的模块
                        includedModules.add(node.namespace)

                        SparkCore.LOGGER.debug("引用资源: {} (模块: {})", resId, node.namespace)
                    }
                }
            }
            
            // 4. 构建资源依赖映射
            val resourceDependencies = mutableMapOf<ResourceLocation, List<OResourceDependency>>()

            // 为每个包含的资源收集其依赖
            for (resId in selectedResources) {
                val deps = ResourceGraphManager.getDirectDependencies(resId)
                if (deps.isNotEmpty()) {
                    resourceDependencies[resId] = deps
                }
            }

            // 构建模块依赖列表
            val moduleDependencies = mutableListOf<String>()
            for (moduleId in includedModules) {
                if (moduleId != newModuleId) { // 不包含自己
                    val moduleNode = ModuleGraphManager.getModuleNode(moduleId)
                    if (moduleNode != null) {
                        moduleDependencies.add(moduleId)
                    }
                }
            }

            // 5. 生成新的模块描述文件 (OModuleInfo)
            val newModuleInfo = OModuleInfo(
                id = newModuleId,
                version = newModuleVersion,
                dependencies = moduleDependencies,
                resourceDependencies = resourceDependencies
            )
            val descriptorFile = tempDir.resolve(OModuleInfo.DESCRIPTOR_FILE_NAME)
            Files.writeString(descriptorFile, gson.toJson(newModuleInfo))

            SparkCore.LOGGER.info("生成模块描述文件: $descriptorFile")
            SparkCore.LOGGER.info("模块依赖: $moduleDependencies")
            SparkCore.LOGGER.info("资源依赖数量: ${resourceDependencies.size}")

            // 6. 将临时目录打包成 .spark 文件
            zipDirectory(tempDir, outputPath)

            return PackagingResult.Success(outputPath, includedFiles.size + 1)
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("创建模块包失败: ${e.message}", e)
            return PackagingResult.Error("创建模块包失败: ${e.message}")
        }
    }

    private fun zipDirectory(sourceDir: Path, zipFilePath: Path) {
        Files.newOutputStream(zipFilePath).use { fos ->
            ZipOutputStream(fos).use { zos ->
                Files.walk(sourceDir).filter { !Files.isDirectory(it) }.forEach { file ->
                    val zipEntry = ZipEntry(sourceDir.relativize(file).toString().replace('\\', '/'))
                    zos.putNextEntry(zipEntry)
                    Files.copy(file, zos)
                    zos.closeEntry()
                }
            }
        }
        SparkCore.LOGGER.info("成功将目录 $sourceDir 打包到 $zipFilePath")
    }

    /**
     * 导出模块为.spark包
     *
     * @param moduleId 模块ID（格式：modId:moduleName）
     * @param outputDir 输出目录
     * @return 打包结果
     */
    fun exportModule(moduleId: String, outputDir: Path): PackagingResult {
        try {
            // 解析模块ID
            val (modId, moduleName) = if (moduleId.contains(":")) {
                val parts = moduleId.split(":", limit = 2)
                Pair(parts[0], parts[1])
            } else {
                // 如果没有冒号，默认为 modId_modId 格式
                Pair(moduleId, moduleId)
            }

            // 生成.spark包文件名：modId_moduleName.spark 或 modId.spark（如果modId == moduleName）
            val sparkFileName = if (modId == moduleName) {
                "$modId.spark"
            } else {
                "${modId}_${moduleName}.spark"
            }

            val outputPath = outputDir.resolve(sparkFileName)

            // 查找模块目录
            val gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
            val moduleDir = gameDir.resolve("sparkcore").resolve(modId).resolve(moduleName)

            if (!Files.exists(moduleDir)) {
                return PackagingResult.Error("模块目录不存在: $moduleDir")
            }

            // 直接打包模块目录
            zipDirectory(moduleDir, outputPath)

            SparkCore.LOGGER.info("成功导出模块 $moduleId 到 $outputPath")
            return PackagingResult.Success(outputPath, 1)

        } catch (e: Exception) {
            SparkCore.LOGGER.error("导出模块失败: ${e.message}", e)
            return PackagingResult.Error("导出模块失败: ${e.message}")
        }
    }

    /**
     * 创建模块模板
     *
     * @param moduleId 模块ID
     * @param templateType 模板类型
     * @param targetPath 目标路径
     * @return 创建结果
     */
    fun createTemplate(moduleId: String, templateType: String, targetPath: Path): PackagingResult {
        try {
            Files.createDirectories(targetPath)

            // 创建基本目录结构
            val animationsDir = targetPath.resolve("animations")
            val modelsDir = targetPath.resolve("models")
            val texturesDir = targetPath.resolve("textures")
            val scriptsDir = targetPath.resolve("scripts")

            Files.createDirectories(animationsDir)
            Files.createDirectories(modelsDir)
            Files.createDirectories(texturesDir)
            Files.createDirectories(scriptsDir)

            SparkCore.LOGGER.info("成功创建模块模板: $targetPath")
            return PackagingResult.Success(targetPath, 4)

        } catch (e: Exception) {
            SparkCore.LOGGER.error("创建模块模板失败: ${e.message}", e)
            return PackagingResult.Error("创建模块模板失败: ${e.message}")
        }
    }

sealed class PackagingResult {
        data class Success(val path: Path, val fileCount: Int) : PackagingResult()
    data class Error(val message: String) : PackagingResult()
    }
}