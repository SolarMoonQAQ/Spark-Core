package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import net.neoforged.fml.loading.FMLPaths
import java.io.File
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.reflect.KClass


fun loadDefaultScripts(c: Class<*>) {
    val gameDir = FMLPaths.GAMEDIR.get().toFile()
    val targetDir = File(gameDir, "sparkcore/script") // 目标目录指向script文件夹
    if (!targetDir.exists()) targetDir.mkdirs()

    // 通过ClassLoader遍历资源目录
    val resourceRoot = c.classLoader.getResource("script")
        ?: throw ModLoadingException(ModLoadingIssue.error("资源目录 /script 不存在"))

    // 使用Files.walk遍历资源目录（支持嵌套）
    Files.walk(Paths.get(resourceRoot.toURI()), FileVisitOption.FOLLOW_LINKS).use { stream ->
        stream.filter { !Files.isDirectory(it) }.forEach { sourcePath ->
            // 计算相对路径
            val relativePath = resourceRoot.toURI().relativize(sourcePath.toUri()).path
            val destFile = targetDir.resolve(relativePath)

            // 只在目标文件不存在时复制
            if (!destFile.exists()) {
                destFile.parentFile?.mkdirs() // 确保父目录存在
                Files.copy(sourcePath, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                SparkCore.LOGGER.info("未找到默认脚本: ${destFile.name}，已更新默认脚本到脚本目录")
            } else {
                SparkCore.LOGGER.debug("脚本已存在: ${destFile.name}，无需更新")
            }
        }
    }
}