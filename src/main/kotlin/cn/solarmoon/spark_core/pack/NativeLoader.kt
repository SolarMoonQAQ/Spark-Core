package cn.solarmoon.spark_core.pack

import cn.solarmoon.spark_core.SparkCore
import com.jme3.bullet.util.NativeLibrary
import com.jme3.system.JmeSystem
import com.jme3.system.Platform.Os.*
import net.neoforged.fml.ModList
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import org.lwjgl.fmod.FMOD
import org.lwjgl.fmod.FMODStudio
import org.lwjgl.system.Configuration
import org.lwjgl.system.Platform as LwjglPlatform
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

object NativeLoader {

    val LOGGER by lazy { SparkCore.logger("Native加载器") }

    /** Spark-Core 模组内的 FMOD 原生资源根目录，供运行时和成品 JAR 探针共用。 */
    const val FMOD_RESOURCE_ROOT = "natives/fmod/2.03.15"

    /** FMOD API 返回的 BCD 版本号，对应随模组打包的 Engine 2.03.15。 */
    const val FMOD_RUNTIME_VERSION = 0x00020315

    private var fmodAvailable: Boolean? = null

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
     * 解析本平台的 native 候选档，**按偏好从强到弱**排列。
     *
     * 候选名同时是资源目录名（`natives/<候选>/<平台>/<架构>/<库文件名>`）与提取缓存的桶名，
     * 因此同名模块换二进制不会命中旧缓存。
     *
     * | 平台 | 候选顺序 | 原因 |
     * |---|---|---|
     * | Windows / Linux | `bullet_dpmt` → `bullet_dp` | 桌面拿双精度 + 多线程求解；上游不提供 macOS 的 Mt 构件 |
     * | macOS | `bullet_dp` | 上游 Mt 的可构建条件含 `(os != 'osx')`，不存在 `MacOSX*-DpMt*.jar` |
     * | Android | `bullet_dp` → `bullet_sp` | 上游未发布 Android 的 Dp 构件；`bullet_dp` 目录留空即可，将来放入 `.so` 自动生效 |
     */
    private fun candidatesFor(platform: com.jme3.system.Platform.Os): List<String> = when (platform) {
        Windows, Linux -> listOf("bullet_dpmt", "bullet_dp")
        MacOS -> listOf("bullet_dp")
        Android -> listOf("bullet_dp", "bullet_sp")
        else -> throw ModLoadingException(ModLoadingIssue.error("Bullet 物理库不支持该系统平台: $platform"))
    }

    /**
     * 加载本地物理库。
     *
     * 按平台的候选表逐个尝试（见 [candidatesFor]）：
     *
     * 1. **资源不存在** → 记一条 info 后跳到下一个候选（为将来预留的空目录属于这种常态，
     *    不再是「加载失败」）；
     * 2. **资源存在但 `System.load` 失败**（`UnsatisfiedLinkError`）→ 先跑依赖诊断
     *    （[buildLoadErrorReport]），记 error 后继续下一个候选；
     * 3. **加载成功** → 立刻用 JNI **实测**档位并打一行汇总。以实测为准，不以模块名推断；
     * 4. 落点弱于「本平台实际投放的最强候选」→ 只警告不阻断启动（决策 D8）；
     *    平台首选档本身就是实际落点时只记 info；
     * 5. 全部候选失败 → 抛 `ModLoadingException`，消息里带上每个候选各自的失败原因。
     *
     * @param modId 模组ID，native 资源与提取缓存都挂在这个模组下
     */
    @JvmStatic
    fun load(modId: String) {
        val platform = JmeSystem.getPlatform().os
        val libFileName = getLibFileName(platform)
        val platformPath = getPlatformPath(platform)
        val archPath = resolveArchPath(platform, System.getProperty("os.arch").lowercase())
        val candidates = candidatesFor(platform)

        // 本平台**实际投放**的最强候选下标；-1 表示一个候选都没投放
        var strongestShipped = -1
        val failures = mutableListOf<String>()
        val attemptedPaths = mutableListOf<String>()

        candidates.forEachIndexed { index, moduleName ->
            val relativePath = "natives/$moduleName/$platformPath/$archPath/$libFileName"
            attemptedPaths += relativePath

            val sourceFile = findResourceOrNull(modId, relativePath)
            if (sourceFile == null) {
                if (strongestShipped < 0) {
                    LOGGER.info("本平台未投放候选档 {}（{}），跳过", moduleName, relativePath)
                }
                return@forEachIndexed
            }
            if (strongestShipped < 0) strongestShipped = index

            val result = tryLoadModule(modId, moduleName, sourceFile, platformPath, archPath, libFileName)
            val error = result.exceptionOrNull()
            if (error == null) {
                reportLoaded(moduleName, index, strongestShipped, candidates, result.getOrThrow())
                return
            }

            val reason = if (error is NativeLoadFailedException) {
                error.cause?.message ?: error.message ?: "unknown error"
            } else {
                error.message ?: error.toString()
            }
            LOGGER.error("====== 物理库候选 ($moduleName) 加载失败 ======")
            LOGGER.error("原因: {}", reason)
            if (error is NativeLoadFailedException) {
                buildLoadErrorReport(error.soPath, libFileName, error.cause as UnsatisfiedLinkError)
            }
            LOGGER.error("==============================================")
            failures += "$moduleName: $reason"
        }

        if (failures.isEmpty()) {
            throw ModLoadingException(ModLoadingIssue.error(
                "物理库加载失败：本平台（$platform/$archPath）没有任何已投放的候选档。已尝试的路径：" +
                        attemptedPaths.joinToString("、")
            ))
        }
        throw ModLoadingException(ModLoadingIssue.error(
            "物理库加载失败：本平台的全部候选档（${candidates.joinToString(" → ")}）均无法加载。\n" +
                    failures.joinToString("\n") { "  - $it" }
        ))
    }

    /**
     * 查询模组内的 native 资源，不存在时返回 null。
     *
     * 模组本身不存在属于致命错误（直接抛）。
     *
     * 注意：`IModFile#findResource` 对**目录形态**的模组（开发环境 `build/resources/main`、测试环境）
     * 只做路径拼接、**不校验存在性**，缺失时会返回一个指向不存在文件的 [Path]。因此这里必须自己确认
     * 它真的是一个普通文件，否则「本平台没有投放这个候选档」会被误报成「候选档加载失败」，
     * 并连带打出一条假的降级警告。
     */
    private fun findResourceOrNull(modId: String, relativePath: String): Path? {
        val path = findModResource(modId, relativePath) ?: return null
        return if (Files.isRegularFile(path)) path else null
    }

    /**
     * 尝试从 Spark-Core 的模组资源加载 FMOD Engine 2.03.15。
     *
     * 由首次使用 FMOD 音效的客户端调用。NeoForge 为开发资源目录和成品 JAR 提供相同的资源路径入口。
     *
     * @return 两个原生库加载成功时返回 true，调用方继续初始化播放。失败时返回 false，跳过 FMOD 音效。
     */
    @JvmStatic
    fun loadFmod(): Boolean = tryLoadFmod {
        findModResource(SparkCore.MOD_ID, FMOD_RESOURCE_ROOT)
            ?: error("未找到 Spark-Core 的 FMOD 资源目录 $FMOD_RESOURCE_ROOT")
    }

    /**
     * 尝试从指定资源根目录加载 FMOD。播放探针通过此入口使用与游戏相同的加载和静音判据。
     *
     * @param nativeRoot FMOD 2.03.15 的资源根目录，可以是磁盘目录，也可以是 JAR 文件系统内的路径
     * @return 是否允许调用方继续初始化 FMOD 播放
     */
    @JvmStatic
    fun loadFmod(nativeRoot: Path): Boolean = tryLoadFmod { nativeRoot }

    /**
     * 串行完成首次加载，并在本进程内复用结果。
     *
     * 各平台加载成功后走相同的播放入口。平台不受支持、资源或系统依赖缺失、绑定初始化失败时按静音处理。
     * 缓存失败可以避免后续音效反复尝试初始化已加载一半的绑定。
     */
    @Synchronized
    private fun tryLoadFmod(resolveRoot: () -> Path): Boolean {
        fmodAvailable?.let { return it }

        val failure = try {
            val android = isAndroidRuntime()
            loadFmodLibraries(resolveRoot().toAbsolutePath().normalize(), android)
            if (android) {
                LOGGER.info("FMOD Android arm64 原生库已加载")
            }
            fmodAvailable = true
            return true
        } catch (e: Exception) {
            e
        } catch (e: LinkageError) {
            e
        }
        fmodAvailable = false
        LOGGER.warn("本次运行将跳过 FMOD 音效：{}", failure.message ?: failure.javaClass.simpleName)
        return false
    }

    /**
     * 识别 Android 进程及 Pojav 系启动器。启动器内嵌的 OpenJDK 可能把系统名报告为 Linux。
     */
    private fun isAndroidRuntime(): Boolean =
        System.getProperty("os.name").equals("Android", ignoreCase = true) ||
                System.getProperty("java.runtime.name").equals("Android Runtime", ignoreCase = true) ||
                System.getenv("ANDROID_ROOT") != null ||
                System.getenv("POJAV_NATIVEDIR") != null

    /**
     * 按平台和 JVM 架构选择库文件，提取后按 Core、Studio 的依赖顺序打开共享库。
     *
     * @param root FMOD 资源根目录
     * @param android JVM 是否由 Android 宿主运行
     */
    private fun loadFmodLibraries(root: Path, android: Boolean) {
        val arch = when (LwjglPlatform.getArchitecture()) {
            LwjglPlatform.Architecture.X64 -> "x86_64"
            LwjglPlatform.Architecture.ARM64 -> "arm64"
            // FMOD 随包提供 64 位库，按 JVM 架构拒绝 32 位进程。
            LwjglPlatform.Architecture.X86, LwjglPlatform.Architecture.ARM32 ->
                error("Spark-Core 的 FMOD 需要 64 位 JVM（x86_64 或 arm64）")
        }
        val (platformPath, coreName, studioName) = if (android) {
            // Pojav 系 OpenJDK 可能报告 Linux，Android 的 Bionic 构件必须优先单独选取。
            check(arch == "arm64") { "Spark-Core 的 FMOD Android 原生库仅提供 arm64-v8a" }
            Triple("android/arm64-v8a", "libfmod.so", "libfmodstudio.so")
        } else {
            when (LwjglPlatform.get()) {
                LwjglPlatform.WINDOWS -> Triple("windows/$arch", "fmod.dll", "fmodstudio.dll")
                // Linux SDK 的实际文件带版本号，按实际文件名查找。macOS 两种架构共用通用二进制。
                LwjglPlatform.LINUX -> Triple("linux/$arch", "libfmod.so.14.15", "libfmodstudio.so.14.15")
                LwjglPlatform.MACOSX -> Triple("macos", "libfmod.dylib", "libfmodstudio.dylib")
            }
        }

        val source = root.resolve(platformPath)
        val core = source.resolve(coreName)
        val studio = source.resolve(studioName)
        // 确认两个库都能提取，再触发 LWJGL 的类初始化。绑定会在初始化时读取库路径配置。
        check(Files.isRegularFile(core) && Files.isRegularFile(studio)) {
            "FMOD Engine 2.03.15 需要同时存在 $core 和 $studio"
        }
        val cache = Paths.get(System.getProperty("java.io.tmpdir"), "spark_core_natives", SparkCore.MOD_ID)
            .resolve(FMOD_RESOURCE_ROOT).resolve(platformPath).toAbsolutePath()
        val cachedCore = cache.resolve(coreName)
        val cachedStudio = cache.resolve(studioName)
        try {
            extractNativeFile(core, cachedCore)
            extractNativeFile(studio, cachedStudio)
        } catch (e: IOException) {
            throw UncheckedIOException("无法将 FMOD Engine 提取到 $cache", e)
        }

        // 通过 LWJGL 打开共享库。Android 路径也只执行库链接，宿主的 JNI 和音频初始化由宿主负责。
        Configuration.FMOD_LIBRARY_NAME.set(cachedCore.toString())
        Configuration.FMOD_STUDIO_LIBRARY_NAME.set(cachedStudio.toString())
        FMOD.getLibrary()
        FMODStudio.getLibrary()
    }

    /** 解析 NeoForge 模组文件内的路径。调用方按文件或目录各自校验资源是否存在。 */
    private fun findModResource(modId: String, relativePath: String): Path? {
        val modFileInfo = ModList.get().getModFileById(modId)
            ?: throw ModLoadingException(ModLoadingIssue.error("未找到模组: $modId"))
        return modFileInfo.file.findResource(relativePath)
    }

    /**
     * 加载成功后的汇总日志与降级警告。
     *
     * 档位一律以 [NativeLibrary] 的 JNI 实测为准：投放目录名与真实 flavor 不一致的情况是
     * 真实发生过的（见文档 §2.2），按目录名推断会把问题藏起来。
     */
    private fun reportLoaded(
        moduleName: String,
        index: Int,
        strongestShipped: Int,
        candidates: List<String>,
        soPath: String
    ) {
        val doublePrecision = NativeLibrary.isDoublePrecision()
        LOGGER.info(
            "物理 native: {}（{} + {}，{} 求解线程，Debug:{}）← {}",
            moduleName,
            if (doublePrecision) "Dp" else "Sp",
            if (NativeLibrary.isThreadSafe()) "Mt" else "非Mt",
            NativeLibrary.countThreads(),
            NativeLibrary.isDebug(),
            soPath
        )

        if (index <= strongestShipped) return
        val preferred = candidates[strongestShipped]
        LOGGER.warn(
            "物理 native 降级：本平台最强投放档 {}（{}）未生效，实际加载 {}（{}）。本次不阻断启动。{}",
            preferred, describeTier(preferred),
            moduleName, describeTier(moduleName),
            degradationHint(preferred, moduleName)
        )
    }

    /** 从投放模块名描述预期档位；仅用于日志文案，实际档位以 JNI 实测为准 */
    private fun describeTier(moduleName: String): String = when {
        moduleName.endsWith("_dpmt") -> "双精度 + 多线程求解"
        moduleName.endsWith("_dp") -> "双精度、单求解线程"
        moduleName.endsWith("_sp") -> "单精度"
        else -> moduleName
    }

    /** 描述从 [preferred] 落到 [loaded] 会失去什么能力 */
    private fun degradationHint(preferred: String, loaded: String): String {
        val lost = mutableListOf<String>()
        if (preferred.contains("dp") && !loaded.contains("dp"))
            lost += "绝对坐标退化为单精度（≳5e5 格处碰撞与地形对齐开始失真）"
        if (preferred.endsWith("mt") && !loaded.endsWith("mt"))
            lost += "求解线程数下降（高负载时物理可能降频）"
        return if (lost.isEmpty()) "" else "影响：" + lost.joinToString("；")
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
     * 尝试加载指定候选档的本地库，返回 Result 避免崩溃
     *
     * @return Result<String>，成功时包含库文件绝对路径，失败时包含异常
     */
    private fun tryLoadModule(
        modId: String,
        moduleName: String,
        sourceFile: Path,
        platformPath: String,
        archPath: String,
        libFileName: String
    ): Result<String> {
        return try {
            val soPath = extractAndPrepareLibrary(modId, moduleName, sourceFile, platformPath, archPath, libFileName)
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
     * @param sourceFile 模组内已定位到的 native 文件
     * @return 解压后的库文件绝对路径
     */
    private fun extractAndPrepareLibrary(
        modId: String,
        moduleName: String,
        sourceFile: Path,
        platformPath: String,
        archPath: String,
        libFileName: String
    ): String {
        val targetDir = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "spark_core_natives",
            modId,
            moduleName,
            platformPath,
            archPath
        )
        val targetFile = targetDir.resolve(libFileName)
        try {
            extractNativeFile(sourceFile, targetFile)
        } catch (e: Exception) {
            throw ModLoadingException(ModLoadingIssue.error(
                "无法解压Native库文件到临时目录: $targetFile（源: $sourceFile，原因: ${e.message}）"
            ))
        }

        return targetFile.toAbsolutePath().toString()
    }

    /**
     * 将原生库复制到可供系统加载的磁盘路径。
     *
     * 缓存的大小和修改时间满足复用条件时直接返回。需要更新时先复制到同目录的临时文件，
     * 再提交完整文件，避免并行启动时读到只复制了一部分的库。
     *
     * @param source 开发目录或模组 JAR 内的库文件
     * @param target 磁盘缓存中的目标文件
     */
    private fun extractNativeFile(source: Path, target: Path) {
        if (Files.exists(target) && Files.size(source) == Files.size(target) &&
            Files.getLastModifiedTime(source) <= Files.getLastModifiedTime(target)
        ) return

        val directory = Files.createDirectories(target.parent)
        val temp = Files.createTempFile(directory, "tmp_", null)
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // 文件系统不支持原子移动时，提交已写完的临时文件。
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
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
