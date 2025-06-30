package cn.solarmoon.spark_core.resource

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.handler.IDynamicResourceHandler
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ResHotReloadService {

    private val POLL_INTERVAL_MS: Long = 1000 // 轮询间隔，例如1秒
    private val monitor = FileAlterationMonitor(POLL_INTERVAL_MS)
    private val isRunning = AtomicBoolean(false)

    // 新增的公共属性，用于外部检查监控是否已启动
    val isMonitorActive: Boolean
        get() = isRunning.get()

    private val directoryMonitors = ConcurrentHashMap<String, Pair<IDynamicResourceHandler, FileAlterationObserver>>()

    fun registerDirectory(directoryId: String, handler: IDynamicResourceHandler) {
        val runtimeDir = Paths.get(System.getProperty("user.dir"), "sparkcore", directoryId).toAbsolutePath()
        val runtimeDirFile = runtimeDir.toFile()

        if (!runtimeDirFile.exists()) {
            SparkCore.LOGGER.warn("目录 {} (ID: {}) 不存在，正在尝试创建。", runtimeDir, directoryId)
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

        SparkCore.LOGGER.info("已为目录 {} (ID: {}) 添加监控，处理器: {}", runtimeDir, directoryId, handler::class.simpleName)

        if (runtimeDirFile.exists() && runtimeDirFile.isDirectory) {
            processExistingFilesInDirectory(runtimeDirFile, handler)
        }
    }

    private fun processExistingFilesInDirectory(directory: File, handler: IDynamicResourceHandler) {
        SparkCore.LOGGER.info("开始处理目录中的现有文件: {} 对应处理器: {}", directory.absolutePath, handler.getResourceType())
        try {
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                try {
                    SparkCore.LOGGER.debug("正在处理现有文件: {}", file.absolutePath)
                    handler.onResourceAdded(file.toPath())
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理现有文件时发生错误 {} 使用处理器 {}: {}", file.absolutePath, handler.getResourceType(), e.message, e)
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("遍历目录 {} 处理现有文件时出错: {}", directory.absolutePath, e.message, e)
        }
        SparkCore.LOGGER.info("完成目录中的现有文件处理: {} 对应处理器: {}", directory.absolutePath, handler.getResourceType())
    }

    fun start() {
        if (directoryMonitors.isEmpty()) {
            SparkCore.LOGGER.info("没有注册的目录，ResHotReloadService (Commons IO) 未启动。")
            return
        }
        if (isRunning.compareAndSet(false, true)) {
            try {
                monitor.start()
                SparkCore.LOGGER.info("ResHotReloadService (Commons IO) 已启动。")
            } catch (e: Exception) {
                isRunning.set(false)
                SparkCore.LOGGER.error("启动 ResHotReloadService (Commons IO) 失败。", e)
            }
        } else {
            SparkCore.LOGGER.warn("ResHotReloadService (Commons IO) 已经运行中。")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                monitor.stop(500) // 尝试在500ms内停止
                directoryMonitors.values.forEach { monitor.removeObserver(it.second) }
                directoryMonitors.clear()
                SparkCore.LOGGER.info("ResHotReloadService (Commons IO) 已停止。")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("停止 ResHotReloadService (Commons IO) 失败或超时。", e)
                // 即使停止失败，也尝试清理
                directoryMonitors.values.forEach { 
                    try { monitor.removeObserver(it.second) } catch (re: Exception) { /* ignore */ }
                }
                directoryMonitors.clear()
            }
        } else {
            SparkCore.LOGGER.warn("ResHotReloadService (Commons IO) 未运行或已在停止过程中。")
        }
    }

    fun unregisterDirectory(directoryId: String) {
        val runtimeDir = Paths.get(System.getProperty("user.dir"), "sparkcore", directoryId).toAbsolutePath()
        val absolutePathStr = runtimeDir.toFile().absolutePath

        directoryMonitors.remove(absolutePathStr)?.let { pair ->
            try {
                monitor.removeObserver(pair.second)
                SparkCore.LOGGER.info("已从 ResHotReloadService (Commons IO) 中取消监控目录: {}", runtimeDir)
            } catch (e: Exception) {
                 SparkCore.LOGGER.error("取消监控目录 {} 时出错: {}", runtimeDir, e.message, e)
            }
        }
    }
    
    fun hasRegisteredDirectories(): Boolean {
        return directoryMonitors.isNotEmpty()
    }
}
