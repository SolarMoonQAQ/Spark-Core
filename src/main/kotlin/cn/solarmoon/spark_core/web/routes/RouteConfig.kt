package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.web.dto.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 路由配置基础设施
 * 
 * 提供路由配置的通用功能和错误处理
 */
object RouteConfig {
    
    /**
     * 创建包含堆栈信息的错误响应
     */
    fun createErrorResponse(message: String, exception: Exception): ApiResponse<Map<String, Any>> {
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
     * 配置所有API路由
     */
    fun Application.configureRoutes() {
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
                                // 基础端点
                                "/api/v1/health",

                                // 动画控制端点
                                "/api/v1/animation/play",
                                "/api/v1/animation/blend",
                                "/api/v1/animation/replace-state",

                                // 模型管理端点
                                "/api/v1/model/load",

                                // 资源管理端点
                                "/api/v1/resources/tree",
                                "/api/v1/resources/search",
                                "/api/v1/resources/{resourceId}",
                                "/api/v1/resources/{resourceId}/status",
                                "/api/v1/resources/stats",
                                "/api/v1/resources/list",
                                "/api/v1/resources/registries",

                                // 依赖编辑端点
                                "/api/v1/dependencies/graph/{resourceId}",
                                "/api/v1/dependencies/add",
                                "/api/v1/dependencies/remove",
                                "/api/v1/dependencies/validate",
                                "/api/v1/dependencies/batch-add",
                                "/api/v1/dependencies/batch-remove",
                                "/api/v1/dependencies/suggestions/{resourceId}",

                                // 打包管理端点
                                "/api/v1/packaging/tasks",
                                "/api/v1/packaging/tasks/{taskId}",
                                "/api/v1/packaging/tasks/{taskId}/execute",
                                "/api/v1/packaging/tasks/{taskId}/cancel",
                                "/api/v1/packaging/tasks/{taskId}/status",
                                "/api/v1/packaging/history",
                                "/api/v1/packaging/auto-packaging/enable",
                                "/api/v1/packaging/auto-packaging/status",
                                "/api/v1/packaging/stats",

                                // 冲突处理端点
                                "/api/v1/conflicts",
                                "/api/v1/conflicts/module/{moduleId}",
                                "/api/v1/conflicts/{resourceId}/diff",
                                "/api/v1/conflicts/{resourceId}/resolve/current",
                                "/api/v1/conflicts/{resourceId}/resolve/legacy",
                                "/api/v1/conflicts/batch-resolve",
                                "/api/v1/conflicts/stats",
                                "/api/v1/conflicts/cleanup-legacy",

                                // WebSocket端点
                                "/api/v1/ws",
                                "/api/v1/ws/stats",

                                // 调试端点
                                "/api/v1/debug/info",
                                "/api/v1/debug/config",
                                "/api/v1/debug/test-error",
                                "/api/v1/debug/gc",

                                // 日志端点
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

                // 配置各个功能模块的路由
                this.configureAnimationRoutes()
                this.configureModelRoutes()
                this.configureLegacyResourceRoutes()
                this.configureDebugRoutes()
                this.configureLogRoutes()
                this.configureResourceManagementRoutes()
                this.configureDependencyRoutes()
                this.configurePackagingRoutes()
                this.configureConflictRoutes()
                this.configureWebSocketRoutes()
            }
        }
    }
}
