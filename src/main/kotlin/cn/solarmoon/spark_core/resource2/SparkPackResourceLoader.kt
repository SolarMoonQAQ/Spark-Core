package cn.solarmoon.spark_core.resource2

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource2.SparkPackLoader.MODULE_NAME
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.use

object SparkPackResourceLoader {

    val registry = mutableMapOf<String, Boolean>()

    @Deprecated("请使用 copyResourceModulesToRun(modId: String)")
    @JvmStatic
    fun copyResourceModulesToRun(classn: Class<*>) {
        copyResourceModulesToRun("spirit_of_fight")
    }

    /**
     * 在当前模组的资源文件中查找核心的同名拓展文件夹，在游戏启动时将其打包为包复制到实际拓展文件夹中
     */
    @JvmStatic
    fun copyResourceModulesToRun(modId: String) {
        registry[modId] = true
        val runModulesDir = FMLPaths.GAMEDIR.get().resolve(MODULE_NAME)
        Files.createDirectories(runModulesDir)
        val modFileInfo = ModList.get().getModFileById(modId)
        val sourceDir = modFileInfo.file.findResource("$MODULE_NAME/")

        try {
            Files.list(sourceDir).use { modules ->
                modules.filter { Files.isDirectory(it) }.forEach { moduleDir ->
                    val zipPath = runModulesDir.resolve("${moduleDir.fileName}.zip")
                    zipModule(moduleDir, zipPath)
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("无法复制 Mod $modId 的资源拓展到核心拓展文件夹: $e")
            return
        }

        SparkCore.LOGGER.info("已复制 Mod $modId 的资源拓展到核心拓展文件夹")
    }

    /**
     * reload时使用，如果在Mod启动时已经启用此加载器，那么在包重载时也会重新打包资源里的文件并覆盖当前的
     */
    fun reload() {
        registry.forEach {
            if (it.value) copyResourceModulesToRun(it.key)
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
        SparkCore.LOGGER.info("已打包模块 ${sourceDir.fileName} 到 $zipPath")
    }

}