package cn.solarmoon.spark_core.pack

import cn.solarmoon.spark_core.SparkCore
import com.jme3.system.JmeSystem
import com.jme3.system.Platform.Os.*
import net.neoforged.fml.ModList
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object NativeLoader {

    val LOGGER = SparkCore.logger("Native加载器")

    fun selectPhysicsLib(): String {
        val platform = JmeSystem.getPlatform()
        val libName = when (platform.os) {
            Windows -> "bulletjme.dll"
            Linux -> "libbulletjme.so"
            MacOS -> "libbulletjme.dylib"
            Android -> "libbulletjme.so"
            else -> throw ModLoadingException(ModLoadingIssue.error("Bullet 物理库不支持该系统平台: ${platform.os}"))
        }
        return libName
    }


    @JvmStatic
    fun load(
        modId: String,
        moduleName: String,
        libName: String
    ) {
        val modFileInfo = ModList.get().getModFileById(modId)
            ?: throw ModLoadingException(
                ModLoadingIssue.error("未找到模组: $modId")
            )
        val sourceDir = modFileInfo.file.findResource("natives/$moduleName")
        val sourceFile = sourceDir.resolve(libName)

        if (!Files.exists(sourceFile)) {
            throw ModLoadingException(ModLoadingIssue.error("未找到Native核心文件: $sourceFile"))
        }

        // 目标目录：系统临时目录下的固定子目录
        val targetDir = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "spark_core_natives",
            modId,
            moduleName
        )
        Files.createDirectories(targetDir)

        val targetFile = targetDir.resolve(libName)

        // 判断是否需要复制：目标文件不存在，或大小/修改时间与源文件不一致
        val needCopy = if (Files.exists(targetFile)) {
            val sourceSize = Files.size(sourceFile)
            val targetSize = Files.size(targetFile)
            val sourceModified = Files.getLastModifiedTime(sourceFile)
            val targetModified = Files.getLastModifiedTime(targetFile)
            sourceSize != targetSize || sourceModified > targetModified
        } else {
            true
        }

        if (needCopy) {
            Files.createDirectories(targetFile.parent)  // 确保子目录存在
            // 原子地复制：先创建临时文件，再移动替换目标文件
            val tempFile = Files.createTempFile(targetDir, "tmp_", null)
            try {
                Files.copy(sourceFile, tempFile, StandardCopyOption.REPLACE_EXISTING)
                try {
                    // 尝试原子移动（避免并发写入导致文件损坏）
                    Files.move(
                        tempFile,
                        targetFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (e: AtomicMoveNotSupportedException) {
                    // 若文件系统不支持原子移动，则回退到普通移动
                    Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                // 清理临时文件后抛出异常
                try {
                    Files.deleteIfExists(tempFile)
                } catch (_: Exception) {
                }
                throw ModLoadingException(ModLoadingIssue.error("无法解压Native库文件到临时目录: $targetFile"))
            }
        }

        // 加载最终的库文件
        System.load(targetFile.toAbsolutePath().toString())
        LOGGER.info("已加载库: ${targetFile.toAbsolutePath()}")
    }

}