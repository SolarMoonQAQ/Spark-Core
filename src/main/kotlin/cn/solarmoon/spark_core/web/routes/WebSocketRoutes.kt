package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.ApiResponse
import cn.solarmoon.spark_core.web.websocket.WebSocketEventBroadcaster
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * WebSocket路由配置
 */
fun Route.configureWebSocketRoutes() {
    route("/ws") {
        // WebSocket连接端点
        get("/") {
            try {
                // 暂时返回WebSocket连接信息，实际WebSocket实现需要添加ktor-server-websockets依赖
                val response = ApiResponse.success(
                    mapOf(
                        "message" to "WebSocket端点可用",
                        "endpoint" to "ws://localhost:8081/api/v1/ws/",
                        "note" to "需要添加ktor-server-websockets依赖以启用WebSocket功能"
                    ),
                    "WebSocket端点信息"
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("WebSocket端点API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("WebSocket端点访问失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // WebSocket连接统计
        get("/stats") {
            try {
                val stats = WebSocketEventBroadcaster.getConnectionStats()
                val response = ApiResponse.success(stats, "WebSocket连接统计")
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取WebSocket统计API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取WebSocket统计失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
