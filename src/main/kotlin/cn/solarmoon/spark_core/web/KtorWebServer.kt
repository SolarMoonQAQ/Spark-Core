package cn.solarmoon.spark_core.web

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.config.SparkConfig
import cn.solarmoon.spark_core.web.dto.ApiResponse
import cn.solarmoon.spark_core.web.routes.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import com.google.gson.Gson
import net.neoforged.neoforge.server.ServerLifecycleHooks
import cn.solarmoon.spark_core.util.ServerThreading
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Ktor Web服务器管理类
 * 
 * 重构后的模块化架构：
 * - 使用独立的路由模块文件
 * - 统一的错误处理机制
 * - 清晰的代码组织结构
 */
object KtorWebServer {
    private var server: EmbeddedServer<*, *>? = null
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
     * 检查服务器是否正在运行
     */
    @JvmStatic
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * 启动Web服务器
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
                            call.respond(HttpStatusCode.OK, "test")
                        }
                    }
                }
                testServer.stop(0, 0) // 立即停止测试服务器
                SparkCore.LOGGER.info("Ktor服务器实例测试成功")
            } catch (testEx: Exception) {
                SparkCore.LOGGER.error("创建Ktor服务器实例失败: ${testEx.message}", testEx)
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

                    SparkCore.LOGGER.info("启动Ktor服务器...")
                    server?.start(wait = false)
                    SparkCore.LOGGER.info("Web服务器启动成功，监听端口: $port")
                    SparkCore.LOGGER.info("API文档地址: http://localhost:$port/api/v1/")

                } catch (e: Exception) {
                    SparkCore.LOGGER.error("启动Web服务器失败: ${e.message}", e)
                    server = null
                }
            }

        } catch (e: Exception) {
            SparkCore.LOGGER.error("启动Web服务器时发生未预期的错误: ${e.message}", e)
        }
    }

    /**
     * 停止Web服务器
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
                SparkCore.LOGGER.warn("Web服务器未运行，无需停止")
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("停止Web服务器时出错: ${e.message}", e)
        }
    }

    /**
     * 配置Ktor服务器
     */
    private fun Application.configureServer(corsEnabled: Boolean) {
        // 配置JSON序列化
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
                setDateFormat("yyyy-MM-dd HH:mm:ss")
            }
        }

        // 配置CORS
        if (corsEnabled) {
            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.AccessControlAllowOrigin)
                allowHeader(HttpHeaders.AccessControlAllowHeaders)
                allowHeader(HttpHeaders.AccessControlAllowMethods)
                anyHost() // 允许所有主机，生产环境中应该限制
            }
        }

        // 配置RESTful API路由
        RouteConfig.run { configureRoutes() }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取服务器世界实例
     * 使用ServerLifecycleHooks获取当前服务器，然后获取主世界
     */
    fun getServerLevel(): net.minecraft.server.level.ServerLevel? {
        try {
            val result = ServerThreading.callOnServerBlocking { it.overworld() }
            if (result != null) {
                SparkCore.LOGGER.debug("成功获取服务器主世界实例")
            } else {
                SparkCore.LOGGER.debug("服务器实例不可用，可能服务器未启动")
            }
            return result
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
    fun getServerPlayer(): net.minecraft.server.level.ServerPlayer? {
        try {
            val player = ServerThreading.callOnServerBlocking { srv -> srv.playerList.players.firstOrNull() }
            if (player != null) {
                SparkCore.LOGGER.debug("获取到服务器玩家: ${'$'}{player.name.string}")
            } else {
                SparkCore.LOGGER.debug("服务器不可用或当前没有在线玩家")
            }
            return player
        } catch (e: Exception) {
            SparkCore.LOGGER.error("获取服务器玩家实例失败", e)
            return null
        }
    }
}
