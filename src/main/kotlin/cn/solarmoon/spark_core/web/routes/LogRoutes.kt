package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.ApiResponse
import cn.solarmoon.spark_core.web.logging.LogSearchRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 日志路由配置
 */
fun Route.configureLogRoutes() {
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
                val errorResponse = RouteConfig.createErrorResponse("日志搜索失败", e)
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
                val errorResponse = RouteConfig.createErrorResponse("获取日志统计信息失败", e)
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
                val errorResponse = RouteConfig.createErrorResponse("清空日志失败", e)
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
                val errorResponse = RouteConfig.createErrorResponse("获取最新日志失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
