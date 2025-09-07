package cn.solarmoon.spark_core.resource2

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.use

object SparkPackResourceLoader {

    val LOGGER = SparkCore.logger("拓展包读取器")

    val registry = mutableListOf<String>()

    /**
     * 在当前模组的资源文件中查找核心的同名拓展文件夹，在游戏启动时将其打包为包复制到实际拓展文件夹中
     */
    @JvmStatic
    fun loadAllModules() {
        ModList.get().mods.forEach {
            registry.add(it.modId)
            loadModule(it.modId)
        }
    }

    fun loadModule(modId: String) {
        val runModulesDir = FMLPaths.GAMEDIR.get().resolve(SparkPackLoader.MODULE_NAME)
        Files.createDirectories(runModulesDir)
        val modFileInfo = ModList.get().getModFileById(modId)
        val sourceDir = modFileInfo.file.findResource("${SparkPackLoader.MODULE_NAME}/")

        try {
            Files.list(sourceDir).use { modules ->
                modules.filter { Files.isDirectory(it) }.forEach { moduleDir ->
                    // 检查 meta.json 是否存在
                    val metaFile = moduleDir.resolve(SparkPackLoader.META_NAME)
                    if (Files.exists(metaFile) && Files.isRegularFile(metaFile)) {
                        val zipPath = runModulesDir.resolve("${moduleDir.fileName}.zip")
                        zipModule(moduleDir, zipPath)
                        LOGGER.info("已打包 Mod $modId 的资源拓展到核心拓展文件夹")
                    }

                    // 复制 .docs 文件夹
                    val docsDir = sourceDir.resolve(".docs")
                    if (Files.exists(docsDir) && Files.isDirectory(docsDir)) {
                        val targetDocsDir = runModulesDir.resolve(".docs").resolve(modId)
                        copyDirectory(docsDir, targetDocsDir)
                        LOGGER.info("已复制 Mod $modId 的 .docs 文件夹到 $targetDocsDir")
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.error("无法打包 Mod $modId 的资源拓展到核心拓展文件夹: $e")
            return
        }
    }

    /**
     * reload时使用，如果在Mod启动时已经启用此加载器，那么在包重载时也会重新打包资源里的文件并覆盖当前的
     */
    fun reload() {
        registry.forEach {
            loadModule(it)
        }
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walk(source).forEach { path ->
            val targetPath = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) {
                Files.createDirectories(targetPath)
            } else {
                Files.createDirectories(targetPath.parent)
                Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun zipModule(sourceDir: Path, zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { zos ->
            Files.walk(sourceDir).filter { Files.isRegularFile(it) }.forEach { file ->
                val entryName = sourceDir.relativize(file).toString().replace("\\", "/")
                zos.putNextEntry(ZipEntry(entryName))
                Files.copy(file, zos)
                zos.closeEntry()
            }
        }
    }

}