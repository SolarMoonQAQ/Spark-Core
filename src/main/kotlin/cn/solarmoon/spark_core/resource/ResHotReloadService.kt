package cn.solarmoon.spark_core.resource

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.common.IResourceHandler
import cn.solarmoon.spark_core.resource.discovery.ResourceDiscoveryService
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 统一的资源热重载服务
 * 重构后使用统一的ResourcePathResolver和IResourceHandler
 */
object ResHotReloadService {

    private val POLL_INTERVAL_MS: Long = 1000 // 轮询间隔
    private val monitor = FileAlterationMonitor(POLL_INTERVAL_MS)
    private val isRunning = AtomicBoolean(false)

    val isMonitorActive: Boolean
        get() = isRunning.get()

    private val directoryMonitors = ConcurrentHashMap<String, Pair<IResourceHandler, FileAlterationObserver>>()

    fun registerDirectory(directoryId: String, handler: IResourceHandler) {
        SparkCore.LOGGER.info("注册目录监控: $directoryId")

        if (ResourceExtractionCompletionTracker.isExtractionComplete()) {
            // 如果资源提取已完成，直接注册
            registerDirectoryInternal(directoryId, handler)
        } else {
            // 否则等待资源提取完成事件
            ResourceExtractionCompletionTracker.onExtractionComplete {
                registerDirectoryInternal(directoryId, handler)
            }
        }
    }
    
    private fun registerDirectoryInternal(directoryId: String, handler: IResourceHandler) {
        SparkCore.LOGGER.info("开始实际注册目录监控: $directoryId")
        
        // 使用ResourceDiscoveryService获取所有命名空间的目录
        val discoveredNamespaces = ResourceDiscoveryService.getDiscoveredNamespaces()
        var registeredAny = false
        
        for ((namespace, namespaceInfo) in discoveredNamespaces.entries) {
            if (directoryId in namespaceInfo.resourceTypes || 
                namespaceInfo.type == ResourceDiscoveryService.ResourceSourceType.SPARK_PACKAGE) {
                
                val runtimeDir = when (namespaceInfo.type) {
                    ResourceDiscoveryService.ResourceSourceType.LOOSE_FILES -> {
                        // 四层结构：namespaceInfo.rootPath 已经指向 run/sparkcore/{modId}/{moduleName}/
                        namespaceInfo.rootPath.resolve(directoryId)
                    }
                    ResourceDiscoveryService.ResourceSourceType.SPARK_PACKAGE -> {
                        // 对于包文件，跳过直接文件监控，由ResourceDiscoveryService处理
                        continue
                    }
                    ResourceDiscoveryService.ResourceSourceType.MOD_ASSETS -> {
                        // 对于MOD_ASSETS，namespace格式为 modId:moduleName
                        val (modId, moduleName) = if (namespace.contains(":")) {
                            val parts = namespace.split(":", limit = 2)
                            Pair(parts[0], parts[1])
                        } else {
                            // fallback：如果没有冒号，使用namespace作为modId和moduleName
                            Pair(namespace, namespace)
                        }
                        namespaceInfo.rootPath.resolve("assets").resolve("sparkcore").resolve(moduleName).resolve(directoryId)
                    }
                    else -> {
                        // 处理其他未知类型
                        continue
                    }
                }.toAbsolutePath()
                
                registerSingleDirectory(runtimeDir, directoryId, handler, namespace)
                registeredAny = true
            }
        }

        if (!registeredAny) {
            SparkCore.LOGGER.warn("未找到目录 {} (ID: {}) 的命名空间信息。", directoryId, directoryId)
        }
    }
    
    private fun registerSingleDirectory(runtimeDir: Path, directoryId: String, handler: IResourceHandler, namespace: String) {
        val runtimeDirFile = runtimeDir.toFile()

        if (!runtimeDirFile.exists()) {
            SparkCore.LOGGER.warn("目录 {} (ID: {}, 命名空间: {}) 不存在，正在尝试创建。", runtimeDir, directoryId, namespace)
            try {
                Files.createDirectories(runtimeDir)
                SparkCore.LOGGER.info("已创建目录: {}", runtimeDir)
            } catch (e: IOException) {
                SparkCore.LOGGER.error("创建目录 {} 失败: {}", runtimeDir, e.message, e)
                return
            }
        }
        if (!runtimeDirFile.isDirectory) {
            SparkCore.LOGGER.error("提供的路径不是一个目录: {} (ID: {})", runtimeDir, directoryId)
            return // 或者抛出异常，取决于您的错误处理策略
        }

        val absolutePathStr = runtimeDirFile.absolutePath
        if (directoryMonitors.containsKey(absolutePathStr)) {
            SparkCore.LOGGER.warn("目录 {} (ID: {}) 已经被注册监控。", runtimeDir, directoryId)
            // 即使监控已存在，也主动触发一次现有文件扫描，
            // 以便在客户端登出清理后、再次进入集成服务器时重新回填动态注册表。
            if (runtimeDirFile.exists() && runtimeDirFile.isDirectory) {
                processExistingFilesInDirectory(runtimeDirFile, handler, directoryId)
            }
            return
        }

        val observer = FileAlterationObserver(runtimeDirFile) // 默认递归监控

        val listener = object : FileAlterationListenerAdaptor() {
            override fun onFileCreate(file: File) {
                SparkCore.LOGGER.info("文件已创建: {}", file.absolutePath)
                try {
                    handler.onResourceAdded(file.toPath())
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理文件创建事件时出错 {} for handler {}: {}", file.absolutePath, handler::class.simpleName, e.message, e)
                }
            }

            override fun onFileChange(file: File) {
                SparkCore.LOGGER.info("文件已修改: {}", file.absolutePath)
                try {
                    handler.onResourceModified(file.toPath())
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理文件修改事件时出错 {} for handler {}: {}", file.absolutePath, handler::class.simpleName, e.message, e)
                }
            }

            override fun onFileDelete(file: File) {
                SparkCore.LOGGER.info("文件已删除: {}", file.absolutePath)
                try {
                    handler.onResourceRemoved(file.toPath())
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理文件删除事件时出错 {} for handler {}: {}", file.absolutePath, handler::class.simpleName, e.message, e)
                }
            }

            override fun onDirectoryCreate(directory: File) {
                SparkCore.LOGGER.info("目录已创建: {}", directory.absolutePath)
                // 如果需要，可以在这里处理目录创建，或者扫描其下的文件
            }

            override fun onDirectoryChange(directory: File) {
                SparkCore.LOGGER.info("目录已修改: {}", directory.absolutePath)
            }

            override fun onDirectoryDelete(directory: File) {
                SparkCore.LOGGER.info("目录已删除: {}", directory.absolutePath)
            }
        }

        observer.addListener(listener)
        monitor.addObserver(observer)
        directoryMonitors[absolutePathStr] = Pair(handler, observer)

        SparkCore.LOGGER.info("已为目录 {} (ID: {}, 命名空间: {}) 添加监控，处理器: {}", runtimeDir, directoryId, namespace, handler::class.simpleName)

        if (runtimeDirFile.exists() && runtimeDirFile.isDirectory) {
            processExistingFilesInDirectory(runtimeDirFile, handler, directoryId)
        }
    }

    private fun processExistingFilesInDirectory(directory: File, handler: IResourceHandler, directoryId: String) {
        try {
            Files.walk(directory.toPath())
                .filter { Files.isRegularFile(it) }
                .filter { handler.canHandle(it) }
                .forEach { filePath ->
                    try {
                        handler.onResourceAdded(filePath)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("处理现有文件失败 {} for handler {}: {}", filePath, handler::class.simpleName, e.message, e)
                    }
                }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("扫描目录现有文件失败 {} for handler {}: {}", directory, handler::class.simpleName, e.message, e)
        }
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            try {
                monitor.start()
                SparkCore.LOGGER.info("文件监控服务已启动")
            } catch (e: Exception) {
                isRunning.set(false)
                SparkCore.LOGGER.error("启动文件监控服务失败: {}", e.message, e)
                throw e
            }
        } else {
            SparkCore.LOGGER.warn("文件监控服务已经在运行")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                monitor.stop()
                SparkCore.LOGGER.info("文件监控服务已停止")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("停止文件监控服务失败: {}", e.message, e)
            }
        } else {
            SparkCore.LOGGER.warn("文件监控服务未在运行")
        }
    }

    fun unregisterDirectory(directoryId: String) {
        // TODO
    }
}
