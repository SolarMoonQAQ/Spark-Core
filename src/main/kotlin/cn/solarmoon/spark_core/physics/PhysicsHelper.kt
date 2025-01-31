package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.SparkCore
import com.google.common.io.Resources
import com.jme3.bullet.util.NativeLibrary
import com.jme3.system.JmeSystem
import com.jme3.system.NativeLibraryLoader
import com.jme3.system.Platform.Os.*
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import net.neoforged.fml.loading.FMLPaths
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption


fun initBullet() {
    val platform = JmeSystem.getPlatform()
    val libName = when(platform.os) {
        Windows -> "bulletjme.dll"
        Linux -> "libbulletjme.so"
        MacOS -> "libbulletjme.dylib"
        else -> throw ModLoadingException(ModLoadingIssue.error("error" + SparkCore.MOD_ID + "init_bullet"))
    }

    val gameDir = FMLPaths.GAMEDIR.get().toFile()

    val targetDir = File(gameDir, "sparkcore")

    if (!targetDir.exists()) { targetDir.mkdirs() }

    val destFile = File(targetDir,  "${platform}ReleaseSp_$libName")

    val resourceStream = SparkCore::class.java.getResourceAsStream("/natives/$libName")
        ?: throw ModLoadingException(ModLoadingIssue.error("未找到物理库核心文件: $libName"))

    resourceStream.use { input ->
        val sourceBytes = resourceStream.readBytes()

        val needUpdate = when {
            !destFile.exists() -> true
            else -> {
                val destBytes = Files.readAllBytes(destFile.toPath())
                !sourceBytes.contentEquals(destBytes)
            }
        }

        if (needUpdate) {
            Files.write(destFile.toPath(), sourceBytes)
            SparkCore.LOGGER.info("已更新物理库核心版本")
        } else {
            SparkCore.LOGGER.info("物理库已是最新版本")
        }
    }

    NativeLibraryLoader.loadLibbulletjme(true, File(gameDir, "sparkcore"), "Release", "Sp")
    NativeLibrary.countThreads()
}