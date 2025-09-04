package cn.solarmoon.spark_core.resource2

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.ModList
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object NativeLoader {

    val LOGGER = SparkCore.logger("Native加载器")

    @JvmStatic
    fun load(moduleName: String, libName: String) {
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
        LOGGER.info("已加载Native库: ${tempFile.absolutePath}.$moduleName/$libName")
    }

}