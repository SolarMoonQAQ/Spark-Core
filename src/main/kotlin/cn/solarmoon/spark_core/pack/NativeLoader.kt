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

    // 根据平台获取库文件名
    private fun getLibFileName(platform: com.jme3.system.Platform.Os): String {
        return when (platform) {
            Windows -> "bulletjme.dll"
            Linux -> "libbulletjme.so"
            MacOS -> "libbulletjme.dylib"
            Android -> "libbulletjme.so"
            else -> throw ModLoadingException(ModLoadingIssue.error("Bullet 物理库不支持该系统平台: $platform"))
        }
    }

    // 获取平台路径组件（小写）
    private fun getPlatformPath(platform: com.jme3.system.Platform.Os): String {
        return when (platform) {
            Windows -> "windows"
            Linux -> "linux"
            MacOS -> "macos"
            Android -> "android"
            else -> throw ModLoadingException(ModLoadingIssue.error("未知平台: $platform"))
        }
    }

    @JvmStatic
    fun load(
        modId: String,
        moduleName: String
    ) {
        val platform = JmeSystem.getPlatform().os
        val libFileName = getLibFileName(platform)

        // 获取原始架构字符串并转为小写
        val rawArch = System.getProperty("os.arch").lowercase()

        // 根据平台规范化架构名称
        val archPath = when (platform) {
            Android -> {
                when {
                    rawArch.contains("aarch64") || rawArch.contains("arm64") -> "arm64-v8a"
                    rawArch.contains("arm") && rawArch.contains("v7") -> "armeabi-v7a"
                    rawArch.contains("x86_64") -> "x86_64"
                    else -> rawArch // 保底
                }
            }
            else -> {
                when {
                    rawArch.contains("aarch64") || rawArch.contains("arm64") -> "arm64"
                    rawArch.contains("amd64") || rawArch.contains("x86_64") -> "x86_64"
                    else -> rawArch
                }
            }
        }

        val platformPath = getPlatformPath(platform)

        // 构建资源相对路径：natives/<moduleName>/<platform>/<arch>/<libFileName>
        val relativePath = "natives/$moduleName/$platformPath/$archPath/$libFileName"

        val modFileInfo = ModList.get().getModFileById(modId)
            ?: throw ModLoadingException(ModLoadingIssue.error("未找到模组: $modId"))

        val sourceFile = modFileInfo.file.findResource(relativePath)
            ?: throw ModLoadingException(ModLoadingIssue.error("未找到Native核心文件: $relativePath"))

        // 目标目录：系统临时目录下按模组、模块、平台、架构划分子目录 TODO: 安卓似乎需要特殊处理路径
        val targetDir = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "spark_core_natives",
            modId,
            moduleName,
            platformPath,
            archPath
        )
        Files.createDirectories(targetDir)

        val targetFile = targetDir.resolve(libFileName)

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
        try {
            System.load(targetFile.toAbsolutePath().toString())
        } catch (e: UnsatisfiedLinkError) {
            throw ModLoadingException(ModLoadingIssue.error(
                "无法加载本地库 $libFileName，可能缺少系统依赖 (如 libgomp1)。" +
                        "在 Docker 中请运行: apt-get install libgomp1"
            ))
        }
        LOGGER.info("已加载库: ${targetFile.toAbsolutePath()}")
    }
}