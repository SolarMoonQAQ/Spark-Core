package cn.solarmoon.spark_core.web

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.config.SparkConfig
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.web.dto.*
import cn.solarmoon.spark_core.web.logging.LogSearchRequest
import cn.solarmoon.spark_core.web.service.ResourceApiService
import com.google.gson.Gson
import java.io.StringWriter
import java.io.PrintWriter
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import net.neoforged.neoforge.server.ServerLifecycleHooks

/**
 * Ktor Web服务器核心类
 * 负责服务器的启动、停止、路由配置和中间件设置
 * 集成CORS支持、JSON序列化、错误处理等功能
 */
object KtorWebServer {
    private var server: EmbeddedServer<*, *>? = null
    private val gson = Gson()
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 创建包含堆栈信息的错误响应
     */
    private fun createErrorResponse(message: String, exception: Exception): ApiResponse<Map<String, Any>> {
        val stackTrace = StringWriter().use { sw ->
            PrintWriter(sw).use { pw ->
                exception.printStackTrace(pw)
                sw.toString()
            }
        }

        val errorData = mapOf(
            "type" to exception.javaClass.simpleName,
            "message" to (exception.message ?: "Unknown error"),
            "stackTrace" to stackTrace
        )

        return ApiResponse(
            success = false,
            data = errorData,
            message = message
        )
    }
    
    /**
     * 启动web服务器
     */
    @JvmStatic
    fun start() {
        try {
            // 检查是否已经启动
            if (isRunning()) {
                SparkCore.LOGGER.warn("Web服务器已经在运行中")
                return
            }
            
            // 读取配置
            val config = SparkConfig.COMMON_CONFIG
            if (!config.enableWebServer.get()) {
                SparkCore.LOGGER.info("Web服务器已禁用，跳过启动")
                return
            }
            
            val port = config.webServerPort.get()
            val corsEnabled = config.webServerCorsEnabled.get()
            
            SparkCore.LOGGER.info("正在启动Web服务器，端口: $port, CORS: $corsEnabled")


            // 先尝试同步创建服务器实例来检查是否有立即的错误
            try {
                SparkCore.LOGGER.info("测试创建Ktor服务器实例...")
                val testServer = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    // 最小配置测试
                    routing {
                        get("/test") {
                            call.respondText("test")
                        }
                    }
                }
                SparkCore.LOGGER.info("测试服务器实例创建成功，开始正式启动...")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("测试创建服务器实例失败: ${e.javaClass.simpleName}: ${e.message}", e)
                return
            }

            // 在协程中启动服务器，避免阻塞游戏主线程
            serverScope.launch {
                try {
                    SparkCore.LOGGER.info("开始创建Ktor服务器实例...")
                    server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                        try {
                            configureServer(corsEnabled)
                            SparkCore.LOGGER.info("Ktor服务器配置完成")
                        } catch (configEx: Exception) {
                            SparkCore.LOGGER.error("配置Ktor服务器时出错: ${configEx.message}", configEx)
                            throw configEx
                        }
                    }
                    SparkCore.LOGGER.info("Ktor服务器实例创建成功")

                    SparkCore.LOGGER.info("开始启动Ktor服务器...")
                    server?.start(wait = false)
                    SparkCore.LOGGER.info("Web服务器启动成功，监听端口: $port")

                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Web服务器启动失败: ${e.javaClass.simpleName}: ${e.message}", e)
                    e.printStackTrace() // 强制打印堆栈跟踪
                    server = null
                }
            }
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("Web服务器启动过程中发生错误", e)
        }
    }
    
    /**
     * 停止web服务器
     */
    @JvmStatic
    fun stop() {
        try {
            server?.let { srv ->
                SparkCore.LOGGER.info("正在停止Web服务器...")
                srv.stop(1000, 2000) // gracePeriod=1s, timeout=2s
                server = null
                SparkCore.LOGGER.info("Web服务器已停止")
            } ?: run {
                SparkCore.LOGGER.debug("Web服务器未运行，无需停止")
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("停止Web服务器时发生错误", e)
        }
    }
    
    /**
     * 检查服务器是否正在运行
     */
    @JvmStatic
    fun isRunning(): Boolean {
        return server != null
    }
    
    /**
     * 配置Ktor应用程序
     */
    private fun Application.configureServer(corsEnabled: Boolean) {
        // 配置JSON序列化
        install(ContentNegotiation) {
            gson {
                // 使用项目统一的Gson配置
                setPrettyPrinting()
                setDateFormat("yyyy-MM-dd HH:mm:ss")
            }
        }
        
        // 配置CORS支持
        if (corsEnabled) {
            install(CORS) {
                anyHost() // 允许所有主机，用于开发调试
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.Accept)
                allowCredentials = true
                maxAgeInSeconds = 3600
            }
        }
        
        // TODO: 配置错误处理中间件（StatusPages插件在当前Ktor版本中需要额外配置）
        // 暂时在路由级别处理错误
        
        // 配置RESTful API路由
        routing {
            // 基础健康检查端点
            get("/health") {
                val response = ApiResponse.success("OK", "Web服务器运行正常")
                call.respond(HttpStatusCode.OK, response)
            }

            // API v1 路由组
            route("/api/v1") {
                // API信息端点
                get("/") {
                    val response = ApiResponse.success(
                        mapOf(
                            "name" to "SparkCore Web API",
                            "version" to "1.0.0",
                            "status" to "running",
                            "endpoints" to listOf(
                                "/api/v1/health",
                                "/api/v1/animation/play",
                                "/api/v1/animation/blend",
                                "/api/v1/animation/replace-state",
                                "/api/v1/model/load",
                                "/api/v1/resources/list",
                                "/api/v1/resources/registries",
                                "/api/v1/debug/info",
                                "/api/v1/debug/config",
                                "/api/v1/debug/test-error",
                                "/api/v1/debug/gc",
                                "/api/v1/logs/search",
                                "/api/v1/logs/stats",
                                "/api/v1/logs/recent",
                                "/api/v1/logs/clear"
                            )
                        ),
                        "API服务正常运行"
                    )
                    call.respond(HttpStatusCode.OK, response)
                }

                // 健康检查端点
                get("/health") {
                    val response = ApiResponse.success("OK", "API服务健康")
                    call.respond(HttpStatusCode.OK, response)
                }

                // 动画控制路由组
                route("/animation") {
                    // 播放动画
                    post("/play") {
                        try {
                            val request = call.receive<AnimationPlayRequest>()
                            SparkCore.LOGGER.info("API请求：播放动画 - name=${request.name}, transTime=${request.transTime}, entityId=${request.entityId}")

                            // 获取服务器世界和玩家（暂时使用模拟实现）
                            val serverLevel = getServerLevel()
                            val serverPlayer = getServerPlayer()

                            if (serverLevel != null && serverPlayer != null) {
                                val response = ResourceApiService.playAnimation(request, serverLevel, serverPlayer)
                                call.respond(HttpStatusCode.OK, response)
                            } else {
                                val response = ApiResponse.error<Boolean>("服务器环境不可用，无法执行动画播放")
                                call.respond(HttpStatusCode.ServiceUnavailable, response)
                            }

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("播放动画API错误", e)
                            val errorResponse = createErrorResponse("播放动画请求处理失败", e)
                            call.respond(HttpStatusCode.BadRequest, errorResponse)
                        }
                    }

                    // 混合动画
                    post("/blend") {
                        try {
                            val request = call.receive<AnimationBlendRequest>()
                            SparkCore.LOGGER.info("API请求：混合动画 - anim1=${request.anim1}, anim2=${request.anim2}, weight=${request.weight}, entityId=${request.entityId}")

                            val serverLevel = getServerLevel()
                            val serverPlayer = getServerPlayer()

                            if (serverLevel != null && serverPlayer != null) {
                                val response = ResourceApiService.blendAnimations(request, serverLevel, serverPlayer)
                                call.respond(HttpStatusCode.OK, response)
                            } else {
                                val response = ApiResponse.error<Boolean>("服务器环境不可用，无法执行动画混合")
                                call.respond(HttpStatusCode.ServiceUnavailable, response)
                            }

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("混合动画API错误", e)
                            val errorResponse = createErrorResponse("混合动画请求处理失败", e)
                            call.respond(HttpStatusCode.BadRequest, errorResponse)
                        }
                    }

                    // 替换状态动画
                    post("/replace-state") {
                        try {
                            val request = call.receive<AnimationReplaceStateRequest>()
                            SparkCore.LOGGER.info("API请求：替换状态动画 - state=${request.state}, animation=${request.animation}, entityId=${request.entityId}")

                            val serverLevel = getServerLevel()
                            val serverPlayer = getServerPlayer()

                            if (serverLevel != null && serverPlayer != null) {
                                val response = ResourceApiService.replaceStateAnimation(request, serverLevel, serverPlayer)
                                call.respond(HttpStatusCode.OK, response)
                            } else {
                                val response = ApiResponse.error<Boolean>("服务器环境不可用，无法执行状态动画替换")
                                call.respond(HttpStatusCode.ServiceUnavailable, response)
                            }

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("替换状态动画API错误", e)
                            val errorResponse = createErrorResponse("替换状态动画请求处理失败", e)
                            call.respond(HttpStatusCode.BadRequest, errorResponse)
                        }
                    }
                }

                // 模型控制路由组
                route("/model") {
                    // 加载模型
                    post("/load") {
                        try {
                            val request = call.receive<ModelLoadRequest>()
                            SparkCore.LOGGER.info("API请求：加载模型 - path=${request.path}, entityId=${request.entityId}")

                            val serverLevel = getServerLevel()
                            val serverPlayer = getServerPlayer()

                            if (serverLevel != null && serverPlayer != null) {
                                val response = ResourceApiService.loadModel(request, serverLevel, serverPlayer)
                                call.respond(HttpStatusCode.OK, response)
                            } else {
                                val response = ApiResponse.error<Boolean>("服务器环境不可用，无法执行模型加载")
                                call.respond(HttpStatusCode.ServiceUnavailable, response)
                            }

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("加载模型API错误", e)
                            val errorResponse = createErrorResponse("加载模型请求处理失败", e)
                            call.respond(HttpStatusCode.BadRequest, errorResponse)
                        }
                    }
                }

                // 资源查询路由组
                route("/resources") {
                    // 列出可用资源
                    get("/list") {
                        try {
                            val resources = mutableMapOf<String, List<String>>()

                            // 获取动画资源
                            SparkRegistries.TYPED_ANIMATION.let { registry ->
                                val animations = mutableListOf<String>()
                                registry.keySet().forEach { location ->
                                    animations.add(location.toString())
                                }
                                resources["animations"] = animations
                            }

                            // 获取模型资源
                            SparkRegistries.MODELS.let { registry ->
                                val models = mutableListOf<String>()
                                registry.keySet().forEach { location ->
                                    models.add(location.toString())
                                }
                                resources["models"] = models
                            }

                            // 获取纹理资源
                            SparkRegistries.DYNAMIC_TEXTURES.let { registry ->
                                val textures = mutableListOf<String>()
                                registry.keySet().forEach { location ->
                                    textures.add(location.toString())
                                }
                                resources["textures"] = textures
                            }

                            // 获取JS脚本资源
                            SparkRegistries.JS_SCRIPTS.let { registry ->
                                val scripts = mutableListOf<String>()
                                registry.keySet().forEach { location ->
                                    scripts.add(location.toString())
                                }
                                resources["scripts"] = scripts
                            }

                            // 获取IK组件资源
                            SparkRegistries.IK_COMPONENT_TYPE.let { registry ->
                                val ikComponents = mutableListOf<String>()
                                registry.keySet().forEach { location ->
                                    ikComponents.add(location.toString())
                                }
                                resources["ik_components"] = ikComponents
                            }

                            val response = ApiResponse.success(resources, "资源列表获取成功")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("获取资源列表API错误", e)
                            val errorResponse = createErrorResponse("获取资源列表失败", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }

                    // 查看注册表状态
                    get("/registries") {
                        try {
                            val registryStatus = mutableMapOf<String, Map<String, Any>>()

                            // 检查各个注册表的状态
                            SparkRegistries.TYPED_ANIMATION.let { registry ->
                                registryStatus["typed_animation"] = mapOf(
                                    "total_entries" to registry.size(),
                                    "registry_key" to registry.key().location().toString(),
                                    "value_type" to "TypedAnimation",
                                    "is_empty" to registry.isEmpty()
                                )
                            }

                            SparkRegistries.MODELS.let { registry ->
                                registryStatus["models"] = mapOf(
                                    "total_entries" to registry.size(),
                                    "registry_key" to registry.key().location().toString(),
                                    "value_type" to "OModel",
                                    "is_empty" to registry.isEmpty()
                                )
                            }

                            SparkRegistries.DYNAMIC_TEXTURES.let { registry ->
                                registryStatus["dynamic_textures"] = mapOf(
                                    "total_entries" to registry.size(),
                                    "registry_key" to registry.key().location().toString(),
                                    "value_type" to "OTexture",
                                    "is_empty" to registry.isEmpty()
                                )
                            }

                            SparkRegistries.JS_SCRIPTS.let { registry ->
                                registryStatus["js_scripts"] = mapOf(
                                    "total_entries" to registry.size(),
                                    "registry_key" to registry.key().location().toString(),
                                    "value_type" to "OJSScript",
                                    "is_empty" to registry.isEmpty()
                                )
                            }

                            SparkRegistries.IK_COMPONENT_TYPE.let { registry ->
                                registryStatus["ik_component_type"] = mapOf(
                                    "total_entries" to registry.size(),
                                    "registry_key" to registry.key().location().toString(),
                                    "value_type" to "TypedIKComponent",
                                    "is_empty" to registry.isEmpty()
                                )
                            }

                            val response = ApiResponse.success(registryStatus, "注册表状态获取成功")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("获取注册表状态API错误", e)
                            val errorResponse = createErrorResponse("获取注册表状态失败", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }
                }

                // 调试信息路由组
                route("/debug") {
                    // 系统信息
                    get("/info") {
                        try {
                            val runtime = Runtime.getRuntime()
                            val systemInfo = mapOf(
                                "server_status" to "running",
                                "web_server_port" to SparkConfig.COMMON_CONFIG.webServerPort.get(),
                                "cors_enabled" to SparkConfig.COMMON_CONFIG.webServerCorsEnabled.get(),
                                "timestamp" to System.currentTimeMillis(),
                                "java_version" to System.getProperty("java.version"),
                                "java_vendor" to System.getProperty("java.vendor"),
                                "os_name" to System.getProperty("os.name"),
                                "os_version" to System.getProperty("os.version"),
                                "available_processors" to runtime.availableProcessors(),
                                "max_memory" to runtime.maxMemory(),
                                "total_memory" to runtime.totalMemory(),
                                "free_memory" to runtime.freeMemory(),
                                "used_memory" to (runtime.totalMemory() - runtime.freeMemory()),
                                "spark_core_version" to SparkCore.MOD_ID,
                            )

                            val response = ApiResponse.success(systemInfo, "系统信息获取成功")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("获取系统信息API错误", e)
                            val errorResponse = createErrorResponse("获取系统信息失败", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }

                    // 配置信息
                    get("/config") {
                        try {
                            val configInfo = mapOf(
                                "web_server" to mapOf(
                                    "enabled" to SparkConfig.COMMON_CONFIG.enableWebServer.get(),
                                    "port" to SparkConfig.COMMON_CONFIG.webServerPort.get(),
                                    "cors_enabled" to SparkConfig.COMMON_CONFIG.webServerCorsEnabled.get()
                                )
                            )

                            val response = ApiResponse.success(configInfo, "配置信息获取成功")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("获取配置信息API错误", e)
                            val errorResponse = createErrorResponse("获取配置信息失败", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }

                    // 测试错误处理（用于调试错误堆栈信息功能）
                    get("/test-error") {
                        try {
                            // 故意抛出异常来测试错误处理
                            throw RuntimeException("这是一个测试异常，用于验证错误堆栈信息功能")
                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("测试错误API", e)
                            val errorResponse = createErrorResponse("测试错误处理功能", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }
                }

                // 日志管理路由组
                route("/logs") {
                    // 搜索日志
                    post("/search") {
                        try {
                            val searchRequest = call.receive<LogSearchRequest>()

                            SparkCore.LOGGER.info("日志搜索请求: level=${searchRequest.level}, side=${searchRequest.side}, regex=${searchRequest.regex}, maxLines=${searchRequest.maxLines}, maxChars=${searchRequest.maxChars}")

                            val logs = SparkCore.ENHANCED_LOGGER.searchLogs(searchRequest)
                            val response = ApiResponse.success(logs, "日志搜索完成，找到 ${logs.size} 条记录")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("日志搜索API错误", e)
                            val errorResponse = createErrorResponse("日志搜索失败", e)
                            call.respond(HttpStatusCode.BadRequest, errorResponse)
                        }
                    }

                    // 获取日志统计信息
                    get("/stats") {
                        try {
                            val stats = SparkCore.ENHANCED_LOGGER.getLogStats()
                            val response = ApiResponse.success(stats, "日志统计信息获取成功")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("获取日志统计信息API错误", e)
                            val errorResponse = createErrorResponse("获取日志统计信息失败", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }

                    // 清空日志
                    delete("/clear") {
                        try {
                            SparkCore.ENHANCED_LOGGER.clearLogs()
                            val response = ApiResponse.success(true, "日志已清空")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("清空日志API错误", e)
                            val errorResponse = createErrorResponse("清空日志失败", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }

                    // 获取最新日志（不需要搜索条件）
                    get("/recent") {
                        try {
                            val maxLines = call.request.queryParameters["maxLines"]?.toIntOrNull() ?: 100
                            val maxChars = call.request.queryParameters["maxChars"]?.toIntOrNull() ?: 50000

                            val searchRequest = LogSearchRequest(
                                maxLines = maxLines,
                                maxChars = maxChars
                            )

                            val logs = SparkCore.ENHANCED_LOGGER.searchLogs(searchRequest)
                            val response = ApiResponse.success(logs, "获取最新日志成功，返回 ${logs.size} 条记录")
                            call.respond(HttpStatusCode.OK, response)

                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("获取最新日志API错误", e)
                            val errorResponse = createErrorResponse("获取最新日志失败", e)
                            call.respond(HttpStatusCode.InternalServerError, errorResponse)
                        }
                    }
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取服务器世界实例
     * 使用ServerLifecycleHooks获取当前服务器，然后获取主世界
     */
    private fun getServerLevel(): net.minecraft.server.level.ServerLevel? {
        try {
            val server = ServerLifecycleHooks.getCurrentServer()
            if (server != null) {
                // 获取主世界（Overworld）
                val overworld = server.overworld()
                SparkCore.LOGGER.debug("成功获取服务器主世界实例")
                return overworld
            } else {
                SparkCore.LOGGER.debug("服务器实例不可用，可能服务器未启动")
                return null
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("获取服务器世界实例失败", e)
            return null
        }
    }

    /**
     * 获取服务器玩家实例
     * 由于Web API没有特定的玩家上下文，这里返回第一个在线玩家
     * 在实际应用中，可能需要通过会话管理或其他方式来确定目标玩家
     */
    private fun getServerPlayer(): net.minecraft.server.level.ServerPlayer? {
        try {
            val server = ServerLifecycleHooks.getCurrentServer()
            if (server != null) {
                val playerList = server.playerList.players
                if (playerList.isNotEmpty()) {
                    val player = playerList[0] // 获取第一个在线玩家
                    SparkCore.LOGGER.debug("获取到服务器玩家: ${player.name.string}")
                    return player
                } else {
                    SparkCore.LOGGER.debug("当前没有在线玩家")
                    return null
                }
            } else {
                SparkCore.LOGGER.debug("服务器实例不可用，可能服务器未启动")
                return null
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("获取服务器玩家实例失败", e)
            return null
        }
    }
}
