package cn.solarmoon.spark_core.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.slf4j.Logger

object ResourceExtractionUtil {
    /**
     * 从 JAR 文件中的目录提取资源到文件系统的目标目录。
     *
     * 行为策略：
     * - 如果目标文件不存在，直接复制JAR中的默认资源
     * - 如果目标文件存在且内容相同，跳过复制
     * - 如果目标文件存在但内容不同，跳过覆盖以保护用户修改
     *
     * @param modMainClass 模组的主类，用于通过其类加载器访问 JAR 资源。
     * @param sourceDirInJar JAR 文件中源目录的路径（例如，\"assets/sparkcore/animations\"）。
     * @param targetBaseRuntimeDir 运行时文件系统中资源将被复制到的基本目录（例如，`run/sparkcore/`）。
     * @param targetSubDirName `targetBaseRuntimeDir` 下特定资源应放置的子目录名称（例如，\"animations\"）。
     * @param logger SLF4J 日志实例，用于记录消息。
     * @return 如果提取成功或不需要提取（例如，源未找到，或文件已存在），返回 true；如果在复制过程中发生严重错误，返回 false。
     */
    fun extractResourcesFromJar(
        modMainClass: Class<*>,
        sourceDirInJar: String,
        targetBaseRuntimeDir: File,
        targetSubDirName: String,
        logger: Logger
    ): Boolean {
        val finalTargetDir = File(targetBaseRuntimeDir, targetSubDirName)
        if (!finalTargetDir.exists()) {
            if (!finalTargetDir.mkdirs()) {
                logger.error("无法创建目标目录: {}", finalTargetDir.absolutePath)
                return false
            }
        }

        val resourceRootUrl = modMainClass.classLoader.getResource(sourceDirInJar)
        if (resourceRootUrl == null) {
            logger.warn("JAR 中未找到源目录 '/{}'。跳过提取到 '{}'。", sourceDirInJar, finalTargetDir.path)
            // 视为成功或非严重失败，因为源可能不存在。如果需要严格失败，请返回 false。
            return true
        }

        return try {
            val resourceRootPath = Paths.get(resourceRootUrl.toURI())
            Files.walk(resourceRootPath).use { stream ->
                stream.forEach { sourcePath -> // 遍历所有路径，包括目录
                    val relativePath = resourceRootPath.relativize(sourcePath).toString()
                    val destFile = File(finalTargetDir, relativePath)

                    if (Files.isDirectory(sourcePath)) {
                        if (!destFile.exists()) {
                            if (!destFile.mkdirs()) {
                                logger.warn("无法创建目录: {}", destFile.absolutePath)
                                // 如果目录创建是关键操作，可以抛出异常或返回 false
                            }
                        }
                    } else if (Files.isRegularFile(sourcePath)) {
                        if (!destFile.exists()) { // 仅在目标文件不存在时复制
                            try {
                                destFile.parentFile?.mkdirs() // 确保父目录存在
                                Files.copy(sourcePath, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                logger.info("已提取默认资源: {}", destFile.path)
                            } catch (e: Exception) {
                                // 记录特定文件复制错误，但继续处理其他文件
                                logger.error("无法将资源文件 '{}' 复制到 '{}': {}", sourcePath, destFile.path, e.message, e)
                                // 根据策略，可能需要设置标志以整体返回 false
                            }
                        } else {
//                            val sourceBytes = Files.readAllBytes(sourcePath)
//                            val destBytes = Files.readAllBytes(destFile.toPath())
//                            if (!sourceBytes.contentEquals(destBytes)) {
//                                // 跳过覆盖，保护用户修改
//                                logger.info("检测到文件已被修改，跳过覆盖以保护用户修改: {}", destFile.path)
//                            } else {
//                                logger.debug("文件内容相同，无需更新: {}", destFile.path)
//                            }
                        }
                    }
                }
            }
            return true
        } catch (e: java.nio.file.FileSystemNotFoundException) {
            logger.error("未找到 URI 的文件系统: {}。如果 JAR 路径未正确解释为文件系统，可能会发生此情况。请确保 JAR 结构正确且可访问。", resourceRootUrl.toURI(), e)
            return false
        } catch (e: Exception) {
            logger.error("无法从 '/{}' 提取默认资源到 '{}': {}", sourceDirInJar, finalTargetDir.path, e.message, e)
            false
        }
    }
}
