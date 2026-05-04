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
import java.util.concurrent.TimeUnit

object NativeLoader {

    val LOGGER = SparkCore.logger("Native加载器")

    /**
     * 已知的 .so 依赖库到各 Linux 发行版软件包的映射
     * key: 去掉版本号的 .so 基础名
     */
    private val KNOWN_DEPENDENCIES: Map<String, DistroPackages> = mapOf(
        "libgomp.so" to DistroPackages(
            deb = "libgomp1",
            rpm = "libgomp",
            alpine = "libgomp",
            desc = "GNU OpenMP runtime (multi-threading support)"
        ),
        "libstdc++.so" to DistroPackages(
            deb = "libstdc++6",
            rpm = "libstdc++",
            alpine = "libstdc++",
            desc = "GNU C++ standard library"
        ),
        "libgcc_s.so" to DistroPackages(
            deb = "libgcc-s1",
            rpm = "libgcc",
            alpine = "libgcc",
            desc = "GCC runtime library"
        ),
        "libm.so" to DistroPackages(
            deb = "libc6",
            rpm = "glibc",
            alpine = "musl",
            desc = "Math library (glibc/musl)"
        ),
        "libc.so" to DistroPackages(
            deb = "libc6",
            rpm = "glibc",
            alpine = "musl",
            desc = "C standard library"
        ),
        "libpthread.so" to DistroPackages(
            deb = "libc6",
            rpm = "glibc",
            alpine = "musl",
            desc = "POSIX threads library"
        ),
    )

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

    /**
     * 加载本地物理库，支持在主库加载失败时自动回退到备选库（如从多线程版回退到单线程版）
     *
     * @param modId 模组ID
     * @param moduleName 主模块名，对应资源路径 natives/<moduleName>/<platform>/<arch>/
     * @param fallbackModuleName 回退模块名，主库加载失败时尝试此模块（如 "bullet_sp"），null 表示不回退
     */
    @JvmStatic
    fun load(
        modId: String,
        moduleName: String,
        fallbackModuleName: String? = null
    ) {
        val platform = JmeSystem.getPlatform().os
        val libFileName = getLibFileName(platform)
        val rawArch = System.getProperty("os.arch").lowercase()
        val archPath = resolveArchPath(platform, rawArch)
        val platformPath = getPlatformPath(platform)

        // 尝试加载主库（如多线程版本 SpMt）
        val primaryResult = tryLoadModule(modId, moduleName, platformPath, archPath, libFileName)
        if (primaryResult.isSuccess) {
            LOGGER.info("已加载多线程物理库: ${primaryResult.getOrThrow()}")
            return
        }

        val primaryError = primaryResult.exceptionOrNull()!!

        // 主库加载失败，先做诊断日志
        LOGGER.error("====== 多线程物理库 ($moduleName) 加载失败 ======")
        LOGGER.error("原因: ${primaryError.message}")
        if (primaryError is NativeLoadFailedException) {
            buildLoadErrorReport(primaryError.soPath, libFileName, primaryError.cause as UnsatisfiedLinkError)
        }
        LOGGER.error("==============================================")

        // 尝试回退到单线程版本
        if (fallbackModuleName != null) {
            LOGGER.error("正在尝试回退到单线程物理库 ($fallbackModuleName)...")
            val fallbackResult = tryLoadModule(modId, fallbackModuleName, platformPath, archPath, libFileName)
            if (fallbackResult.isSuccess) {
                LOGGER.info("已成功加载回退的单线程物理库: ${fallbackResult.getOrThrow()}")
                return
            }

            val fallbackError = fallbackResult.exceptionOrNull()!!
            val fallbackMsg = if (fallbackError is NativeLoadFailedException) {
                "回退库加载错误: ${fallbackError.cause?.message ?: fallbackError.message}"
            } else {
                "回退库加载错误: ${fallbackError.message}"
            }

            LOGGER.error("====== 单线程物理库 ($fallbackModuleName) 也加载失败 ======")
            LOGGER.error(fallbackMsg)
            if (fallbackError is NativeLoadFailedException) {
                buildLoadErrorReport(fallbackError.soPath, libFileName, fallbackError.cause as UnsatisfiedLinkError)
            }
            LOGGER.error("======================================================")

            throw ModLoadingException(ModLoadingIssue.error(
                "物理库加载失败：主库多线程版本 ($moduleName) 和回退单线程版本 ($fallbackModuleName) 均无法加载。"
            ))
        }

        // 没有配置回退，直接抛出原始错误
        if (primaryError is NativeLoadFailedException) {
            throw ModLoadingException(ModLoadingIssue.error(
                "物理库 ($moduleName) 加载失败：${primaryError.cause?.message ?: primaryError.message}"
            ))
        }
        throw primaryError
    }

    /**
     * 解析规范化后的架构路径
     */
    private fun resolveArchPath(platform: com.jme3.system.Platform.Os, rawArch: String): String {
        return when (platform) {
            Android -> {
                when {
                    rawArch.contains("aarch64") || rawArch.contains("arm64") -> "arm64-v8a"
                    rawArch.contains("arm") && rawArch.contains("v7") -> "armeabi-v7a"
                    rawArch.contains("x86_64") -> "x86_64"
                    else -> rawArch
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
    }

    /**
     * 尝试加载指定模块的本地库，返回 Result 避免崩溃
     *
     * @return Result<String>，成功时包含库文件绝对路径，失败时包含异常
     */
    private fun tryLoadModule(
        modId: String,
        moduleName: String,
        platformPath: String,
        archPath: String,
        libFileName: String
    ): Result<String> {
        return try {
            val soPath = extractAndPrepareLibrary(modId, moduleName, platformPath, archPath, libFileName)
            try {
                System.load(soPath)
            } catch (e: UnsatisfiedLinkError) {
                return Result.failure(NativeLoadFailedException(soPath, libFileName, e))
            }
            Result.success(soPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从模组资源中提取本地库文件到临时目录
     *
     * @return 解压后的库文件绝对路径
     */
    private fun extractAndPrepareLibrary(
        modId: String,
        moduleName: String,
        platformPath: String,
        archPath: String,
        libFileName: String
    ): String {
        val relativePath = "natives/$moduleName/$platformPath/$archPath/$libFileName"

        val modFileInfo = ModList.get().getModFileById(modId)
            ?: throw ModLoadingException(ModLoadingIssue.error("未找到模组: $modId"))

        val sourceFile = modFileInfo.file.findResource(relativePath)
            ?: throw ModLoadingException(ModLoadingIssue.error("未找到Native核心文件: $relativePath"))

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
            Files.createDirectories(targetFile.parent)
            val tempFile = Files.createTempFile(targetDir, "tmp_", null)
            try {
                Files.copy(sourceFile, tempFile, StandardCopyOption.REPLACE_EXISTING)
                try {
                    Files.move(
                        tempFile,
                        targetFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (e: AtomicMoveNotSupportedException) {
                    Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                try {
                    Files.deleteIfExists(tempFile)
                } catch (_: Exception) {
                }
                throw ModLoadingException(ModLoadingIssue.error("无法解压Native库文件到临时目录: $targetFile"))
            }
        }

        return targetFile.toAbsolutePath().toString()
    }

    /**
     * 通过 ldd 命令检测 .so 文件缺失的系统动态库依赖
     *
     * @param soPath 已解压到磁盘的 .so 文件绝对路径
     * @return 缺失依赖列表 (库基础名 -> ldd 原始行)，若 ldd 不可用则返回空 Map
     */
    private fun detectMissingDependencies(soPath: String): Map<String, String> {
        val missing = linkedMapOf<String, String>()
        try {
            val process = ProcessBuilder("ldd", soPath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(5, TimeUnit.SECONDS)

            for (line in output.lines()) {
                if (line.contains("not found")) {
                    // 典型格式: "    libgomp.so.1 => not found"
                    val baseName = line
                        .substringBefore("=>")
                        .trim()
                        .replace(Regex("\\.so\\.[\\d.]+$"), ".so")
                    missing[baseName] = line.trim()
                }
            }
        } catch (_: Exception) {
            // ldd 不可用或执行失败，调用方应降级到 UnsatisfiedLinkError 解析
        }
        return missing
    }

    /**
     * 从 UnsatisfiedLinkError 消息中提取第一个缺失的 .so 基础名
     * Java 消息格式: "/path/to/lib.so: libgomp.so.1: cannot open shared object file: No such file or directory"
     */
    private fun parseMissingLibFromError(error: UnsatisfiedLinkError): String? {
        val msg = error.message ?: return null
        // 匹配 ": libXXX.so.N: cannot open" 模式
        val pattern = Regex(":\\s+(\\S+\\.so[\\d.]*)\\s*:")
        return pattern.find(msg)?.groupValues?.getOrNull(1)
            ?.replace(Regex("\\.so\\.[\\d.]+$"), ".so")
    }

    /**
     * 检测 Linux 发行版类型，用于生成准确的包安装命令
     */
    private fun detectDistro(): DistroType {
        try {
            val osRelease = Files.readString(Paths.get("/etc/os-release"))
            if (osRelease.contains("alpine", ignoreCase = true)) return DistroType.ALPINE
            if (osRelease.contains("centos", ignoreCase = true) ||
                osRelease.contains("rhel", ignoreCase = true) ||
                osRelease.contains("fedora", ignoreCase = true)) return DistroType.RPM
        } catch (_: Exception) {
        }
        if (Files.exists(Paths.get("/usr/bin/apt")) || Files.exists(Paths.get("/usr/bin/apt-get")))
            return DistroType.DEB
        if (Files.exists(Paths.get("/usr/bin/yum")) || Files.exists(Paths.get("/usr/bin/dnf")))
            return DistroType.RPM
        if (Files.exists(Paths.get("/sbin/apk")))
            return DistroType.ALPINE
        return DistroType.UNKNOWN
    }

    /**
     * 根据发行版类型和缺失库列表生成安装命令
     */
    private fun buildInstallCommand(
        missingLibs: Map<String, String>,
        distro: DistroType
    ): String {
        val packages = missingLibs.keys.mapNotNull { KNOWN_DEPENDENCIES[it] }
        if (packages.isEmpty()) return ""

        return when (distro) {
            DistroType.DEB -> {
                val pkgsStr = packages.joinToString(" ") { it.deb }
                "sudo apt-get update && sudo apt-get install -y $pkgsStr"
            }
            DistroType.RPM -> {
                val pkgsStr = packages.joinToString(" ") { it.rpm }
                "sudo yum install -y $pkgsStr"
            }
            DistroType.ALPINE -> {
                val pkgsStr = packages.joinToString(" ") { it.alpine }
                "apk add --no-cache $pkgsStr"
            }
            DistroType.UNKNOWN -> {
                // 未知发行版：列出所有三种命令
                val debPkgs = packages.joinToString(" ") { it.deb }
                val rpmPkgs = packages.joinToString(" ") { it.rpm }
                val apkPkgs = packages.joinToString(" ") { it.alpine }
                "(Debian/Ubuntu) sudo apt-get install -y $debPkgs\n" +
                        "  (RHEL/CentOS)   sudo yum install -y $rpmPkgs\n" +
                        "  (Alpine/Docker) apk add --no-cache $apkPkgs"
            }
        }
    }

    /**
     * 构建完整的加载失败诊断报告，并输出到日志
     *
     * @return 包含英文错误摘要的报告，用于崩溃日志
     */
    private fun buildLoadErrorReport(
        soPath: String,
        libFileName: String,
        error: UnsatisfiedLinkError
    ): LoadErrorReport {
        val missingByLdd = detectMissingDependencies(soPath)

        if (missingByLdd.isNotEmpty()) {
            val distro = detectDistro()
            val installCmd = buildInstallCommand(missingByLdd, distro)
            val libList = missingByLdd.keys.joinToString(", ")

            val shortMsg = "Failed to load $libFileName: missing system libraries [$libList]. " +
                    "Run: $installCmd"

            LOGGER.error("====== 本地库加载失败 ======")
            LOGGER.error("文件: $soPath")
            LOGGER.error("已检测到以下缺失的系统依赖:")
            for ((lib, line) in missingByLdd) {
                val desc = KNOWN_DEPENDENCIES[lib]?.desc ?: "unknown"
                LOGGER.error("  $line  [$desc]")
            }
            LOGGER.error("安装命令: $installCmd")
            LOGGER.error("==============================")

            return LoadErrorReport(shortMsg)
        }

        val parsedLib = parseMissingLibFromError(error)
        val originMsg = error.message ?: "unknown error"

        val shortMsg = if (parsedLib != null) {
            "Failed to load $libFileName: $originMsg. " +
                    "Hint: try 'apt-get install libgomp1' or see full log for details."
        } else {
            "Failed to load $libFileName: $originMsg. " +
                    "Hint: install libgomp1/libstdc++6, or see full log for details."
        }

        LOGGER.error("====== 本地库加载失败 ======")
        LOGGER.error("文件: $soPath")
        LOGGER.error("原始错误: $originMsg")
        if (parsedLib != null) {
            val desc = KNOWN_DEPENDENCIES[parsedLib]?.desc
            val deb = KNOWN_DEPENDENCIES[parsedLib]?.deb
            LOGGER.error("缺失库: $parsedLib${if (desc != null) " ($desc)" else ""}")
            if (deb != null) LOGGER.error("请尝试: sudo apt-get install -y $deb")
        }
        LOGGER.error("==============================")

        return LoadErrorReport(shortMsg)
    }
}

/**
 * 本地库加载失败的内部异常，携带解压后的库文件路径用于后续诊断
 */
private class NativeLoadFailedException(
    val soPath: String,
    val libFileName: String,
    cause: UnsatisfiedLinkError
) : Exception(cause)

/**
 * 发行版软件包名称
 */
private data class DistroPackages(
    val deb: String,
    val rpm: String,
    val alpine: String,
    val desc: String
)

/**
 * Linux 发行版类型
 */
private enum class DistroType { DEB, RPM, ALPINE, UNKNOWN }

/**
 * 加载错误报告，shortMessage 用于崩溃日志（纯英文），详情通过 LOGGER 输出
 */
private data class LoadErrorReport(val shortMessage: String)