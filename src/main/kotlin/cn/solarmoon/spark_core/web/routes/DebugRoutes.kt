package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.config.SparkConfig
import cn.solarmoon.spark_core.web.dto.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 调试路由配置
 */
fun Route.configureDebugRoutes() {
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
                val errorResponse = RouteConfig.createErrorResponse("获取系统信息失败", e)
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
                val errorResponse = RouteConfig.createErrorResponse("获取配置信息失败", e)
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
                val errorResponse = RouteConfig.createErrorResponse("测试错误处理功能", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 垃圾回收
        post("/gc") {
            try {
                val beforeGC = Runtime.getRuntime().let { runtime ->
                    mapOf(
                        "total_memory" to runtime.totalMemory(),
                        "free_memory" to runtime.freeMemory(),
                        "used_memory" to (runtime.totalMemory() - runtime.freeMemory())
                    )
                }

                System.gc()

                val afterGC = Runtime.getRuntime().let { runtime ->
                    mapOf(
                        "total_memory" to runtime.totalMemory(),
                        "free_memory" to runtime.freeMemory(),
                        "used_memory" to (runtime.totalMemory() - runtime.freeMemory())
                    )
                }

                val gcInfo = mapOf(
                    "before_gc" to beforeGC,
                    "after_gc" to afterGC,
                    "memory_freed" to (beforeGC["used_memory"] as Long - afterGC["used_memory"] as Long),
                    "timestamp" to System.currentTimeMillis()
                )

                val response = ApiResponse.success(gcInfo, "垃圾回收执行完成")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                SparkCore.LOGGER.error("执行垃圾回收API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("执行垃圾回收失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
