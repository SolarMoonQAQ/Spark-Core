package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.ServerSparkJS
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.payload.resource_sync.ChangeType
import cn.solarmoon.spark_core.util.ResourceExtractionUtil
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

@AutoRegisterHandler
class DynamicJavaScriptHandler(
    internal val jsRegistry: DynamicAwareRegistry<OJSScript>
) : IDynamicResourceHandler {
    private val directoryIdString = "script"
    private var initialScanComplete = false
    
    // 与其他处理器保持一致的路径配置
    private val baseScriptPath: Path = FMLPaths.GAMEDIR.get().resolve("sparkcore").resolve(getDirectoryId())
        .also { path ->
            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path)
                    SparkCore.LOGGER.info("Created base directory for DynamicJavaScriptHandler: {}", path)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Failed to create base directory for DynamicJavaScriptHandler {}: {}", path, e.message)
                    throw IllegalStateException("Failed to create base directory: $path", e)
                }
            } else if (!Files.isDirectory(path)) {
                 throw IllegalArgumentException("Base path '$path' exists but is not a directory.")
            }
        }

    init {
        SparkCore.LOGGER.info(
            "DynamicJavaScriptHandler 初始化完成，监控目录ID: {}, 完整路径: {}",
            getDirectoryId(),
            baseScriptPath
        )
    }

    override fun getDirectoryPath(): String {
        return baseScriptPath.toString()
    }

    override fun getResourceType(): String {
        return "JavaScript脚本"
    }
    
    fun markInitialScanComplete() {
        this.initialScanComplete = true
        SparkCore.LOGGER.info("DynamicJavaScriptHandler (${getResourceType()}) 标记初始扫描完成。后续资源变动将触发热重载。")
    }

    /**
     * 确定脚本文件对应的API模块
     * 路径映射规则: sparkcore/script/[api_id]/xxx.js
     */
    private fun determineApiFromPath(file: Path): JSApi? {
        if (!file.startsWith(baseScriptPath)) {
            SparkCore.LOGGER.warn("文件 $file 不在基础路径 $baseScriptPath 下。")
            return null
        }

        val relativePath = baseScriptPath.relativize(file)
        val pathParts = relativePath.toString().split("/", "\\")
        
        if (pathParts.isEmpty()) {
            SparkCore.LOGGER.warn("无法从路径 $relativePath 确定API模块")
            return null
        }

        val apiId = pathParts[0]
        SparkCore.LOGGER.debug("从路径 {} 解析出API ID: {}, 已注册的API: {}", file, apiId, JSApi.ALL.keys)
        return JSApi.ALL[apiId]?.also {
            SparkCore.LOGGER.debug("路径 {} 映射到API模块: {}", file, apiId)
        } ?: run {
            SparkCore.LOGGER.warn("未找到API模块: {} 对应文件: {}, 可用的API模块: {}", apiId, file, JSApi.ALL.keys)
            null
        }
    }

    /**
     * 创建脚本的ResourceLocation
     */
    private fun createScriptLocation(apiId: String, fileName: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "script/${apiId}/${fileName}")
    }

    /**
     * 获取服务端JS引擎实例
     */
    private fun getServerJSEngine(): ServerSparkJS? {
        return try {
            val server = ServerLifecycleHooks.getCurrentServer()
            server?.overworld()?.jsEngine as? ServerSparkJS
        } catch (e: Exception) {
            SparkCore.LOGGER.debug("无法获取服务端JS引擎: {}", e.message)
            null
        }
    }

    /**
     * 处理单个脚本文件的增量同步
     */
    private fun handleSingleScriptChange(file: Path, operationType: ChangeType) {
        try {
            SparkCore.LOGGER.debug("handleSingleScriptChange 开始处理: 文件={}, 操作类型={}, 初始扫描完成={}", file, operationType, initialScanComplete)
            
            // 在初始扫描阶段，即使没有服务端也要注册脚本到注册表
            // 在运行时阶段，才需要检查服务端实例
            if (initialScanComplete) {
                // 检查是否能获取到服务端实例，这支持专用服务器和集成服务器
                val server = try {
                    ServerLifecycleHooks.getCurrentServer()
                } catch (e: Exception) {
                    null
                }
                
                if (server == null) {
                    SparkCore.LOGGER.debug("运行时无服务端环境跳过脚本处理，等待服务端同步")
                    return
                }
            }

            val api = determineApiFromPath(file) ?: run {
                SparkCore.LOGGER.warn("handleSingleScriptChange: 无法确定API模块，跳过处理文件: {}", file)
                return
            }
            val fileName = file.fileName.toString()
            val location = createScriptLocation(api.id, fileName)

            when (operationType) {
                ChangeType.ADDED,
                ChangeType.MODIFIED -> {
                    // 读取脚本内容
                    val scriptContent = file.readText()
                    
                    // 创建OJSScript对象
                    val ojsScript = OJSScript(
                        apiId = api.id,
                        fileName = fileName,
                        content = scriptContent,
                        location = location
                    )
                    
                    // 注册到动态注册表，触发网络同步
                    if (initialScanComplete) {
                        jsRegistry.registerDynamic(location, ojsScript)
                        SparkCore.LOGGER.info("成功注册JS脚本到动态注册表: {}", location)
                    } else {
                        // 初始扫描阶段，只本地注册，不触发网络同步
                        val resourceKey = net.minecraft.resources.ResourceKey.create(jsRegistry.key(), location)
                        jsRegistry.register(resourceKey, ojsScript, net.minecraft.core.RegistrationInfo.BUILT_IN)
                        SparkCore.LOGGER.debug("初始扫描：JS脚本已本地注册: {}", location)
                    }
                    
                    // 在服务端执行脚本和更新API缓存（仅在运行时阶段）
                    if (initialScanComplete) {
                        val jsEngine = getServerJSEngine()
                        if (jsEngine != null) {
                            jsEngine.eval(scriptContent, "${api.id} - $fileName")
                            // 移除手动更新valueCache，现在从动态注册表自动获取
                            // api.valueCache[fileName] = scriptContent
                            
                            // 如果是添加操作，触发onLoad
                            if (operationType == ChangeType.ADDED) {
                                api.onLoad()
                            }
                            
                            val operation = if (operationType == ChangeType.MODIFIED) "更新" else "添加"
                            SparkCore.LOGGER.info("服务端${operation}JS脚本: API={}, 文件={}", api.id, fileName)
                        } else {
                            SparkCore.LOGGER.warn("无法获取服务端JS引擎，跳过执行: {}", api.id)
                        }
                    } else {
                        SparkCore.LOGGER.debug("初始扫描阶段：脚本已注册但暂不执行: {}", location)
                    }
                }
                
                ChangeType.REMOVED -> {
                    // 从动态注册表移除，触发网络同步
                    if (initialScanComplete) {
                        jsRegistry.unregisterDynamic(location)
                        SparkCore.LOGGER.info("从动态注册表移除JS脚本: {}", location)
                    } else {
                        // 初始扫描阶段，只本地移除
                        jsRegistry.unregisterDynamic(location)
                        SparkCore.LOGGER.debug("初始扫描：JS脚本已移除: {}", location)
                    }
                    
                    // 从缓存中移除并触发reload（仅在运行时阶段）
                    if (initialScanComplete) {
                        // 移除手动操作valueCache，现在从动态注册表自动获取
                        // api.valueCache.remove(fileName)
                        api.onReload()
                        
                        SparkCore.LOGGER.info("服务端删除JS脚本: API={}, 文件={}", api.id, fileName)
                    } else {
                        SparkCore.LOGGER.debug("初始扫描阶段：脚本已移除: {}", location)
                    }
                }
            }
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("处理脚本变更时发生错误: 文件={}", file, e)
        }
    }

    override fun onResourceAdded(file: Path) {
        SparkCore.LOGGER.debug("检测到脚本文件添加: {}", file)
        
        // 只处理JavaScript文件
        val fileName = file.fileName.toString().lowercase()
        if (!fileName.endsWith(".js")) {
            SparkCore.LOGGER.debug("跳过非JavaScript文件: {}", file)
            return
        }

        SparkCore.LOGGER.debug("准备处理JavaScript文件: {}", file)
        handleSingleScriptChange(file, ChangeType.ADDED)
        SparkCore.LOGGER.debug("JavaScript文件处理完成: {}", file)
    }

    override fun onResourceModified(file: Path) {
        SparkCore.LOGGER.debug("检测到脚本文件修改: {}", file)
        
        // 只处理JavaScript文件
        val fileName = file.fileName.toString().lowercase()
        if (!fileName.endsWith(".js")) {
            SparkCore.LOGGER.debug("跳过非JavaScript文件: {}", file)
            return
        }

        handleSingleScriptChange(file, ChangeType.MODIFIED)
    }

    override fun onResourceRemoved(file: Path) {
        SparkCore.LOGGER.debug("检测到脚本文件移除: {}", file)
        
        // 只处理JavaScript文件（即使文件已删除，我们仍可从路径判断）
        val fileName = file.fileName.toString().lowercase()
        if (!fileName.endsWith(".js")) {
            SparkCore.LOGGER.debug("跳过非JavaScript文件: {}", file)
            return
        }

        handleSingleScriptChange(file, ChangeType.REMOVED)
    }

    override fun getDirectoryId(): String {
        return directoryIdString
    }

    override fun getRegistryIdentifier(): ResourceLocation? {
        return jsRegistry.key().location() // 现在使用动态注册表
    }

    override fun getSourceResourceDirectoryName(): String = "script"

    override fun initializeDefaultResources(modMainClass: Class<*>): Boolean {
        val gameDir = FMLPaths.GAMEDIR.get().toFile()
        val targetRuntimeBaseDir = File(gameDir, "sparkcore")
        val success = ResourceExtractionUtil.extractResourcesFromJar(
            modMainClass,
            getSourceResourceDirectoryName(),      // "script"
            targetRuntimeBaseDir,
            getDirectoryId(),                      // "script"
            SparkCore.LOGGER
        )
        
        // 如果提取成功，处理所有提取的脚本文件
        if (success) {
            val finalTargetDir = File(targetRuntimeBaseDir, getDirectoryId())
            for (file in finalTargetDir.walk()) {
                if (file.isFile && file.extension.lowercase() == "js") {
                    // 使用增量同步机制处理初始脚本
                    handleSingleScriptChange(file.toPath(), ChangeType.ADDED)
                }
            }
        }
        
        SparkCore.LOGGER.info("JavaScript脚本默认资源初始化完成: {}", if (success) "成功" else "失败")
        return success
    }
} 