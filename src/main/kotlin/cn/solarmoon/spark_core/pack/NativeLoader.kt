package cn.solarmoon.spark_core.pack

import cn.solarmoon.spark_core.SparkCore
import electrostatic4j.snaploader.LibraryInfo
import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.filesystem.DirectoryPath
import electrostatic4j.snaploader.platform.NativeDynamicLibrary
import electrostatic4j.snaploader.platform.util.PlatformPredicate
import net.neoforged.fml.ModList
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object NativeLoader {

    val LOGGER = SparkCore.logger("Native加载器")

    @JvmStatic
    fun load(moduleName: String, libName: String) {
        val info = LibraryInfo(null, "bulletjme", DirectoryPath.USER_DIR)
        val loader = NativeBinaryLoader(info)

        val libraries = arrayOf(
            NativeDynamicLibrary("native/linux/arm64", PlatformPredicate.LINUX_ARM_64),
            NativeDynamicLibrary("native/linux/arm32", PlatformPredicate.LINUX_ARM_32),
            NativeDynamicLibrary("native/linux/x86_64", PlatformPredicate.LINUX_X86_64),
            NativeDynamicLibrary("native/osx/arm64", PlatformPredicate.MACOS_ARM_64),
            NativeDynamicLibrary("native/osx/x86_64", PlatformPredicate.MACOS_X86_64),
            NativeDynamicLibrary("native/windows/x86_64", PlatformPredicate.WIN_X86_64)
        )
        loader.registerNativeLibraries(libraries)
            .initPlatformLibrary()
            .setLoggingEnabled(true)
        loader.isRetryWithCleanExtraction = true

        // Load the Libbulletjme native library for this platform.
        try {
            loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION)
        } catch (exception: Exception) {
            throw IllegalStateException("Failed to load the Libbulletjme library!")
        }

        val modFileInfo = ModList.get().getModFileById(SparkCore.MOD_ID)
        val sourceDir = modFileInfo.file.findResource("natives/$moduleName")
        val nativeFile = sourceDir.resolve(libName)

        if (!Files.exists(nativeFile)) { throw ModLoadingException(ModLoadingIssue.error("未找到Native核心文件: $nativeFile")) }

        // 解压到临时文件
        val tempFile = Files.createTempFile("spark_core.", "").toFile()
        tempFile.deleteOnExit()
        Files.copy(nativeFile, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        // 直接加载
        System.load(tempFile.absolutePath)
        LOGGER.info("已加载库: ${tempFile.absolutePath}.$moduleName/$libName")
    }

}