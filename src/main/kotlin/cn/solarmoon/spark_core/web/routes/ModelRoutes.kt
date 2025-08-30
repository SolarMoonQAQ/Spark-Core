package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.*
import cn.solarmoon.spark_core.web.service.ResourceApiService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.neoforged.neoforge.server.ServerLifecycleHooks
import cn.solarmoon.spark_core.util.ServerThreading

/**
 * 模型控制路由配置
 */
fun Route.configureModelRoutes() {
    route("/model") {
        // 加载模型
        post("/load") {
            try {
                val request = call.receive<ModelLoadRequest>()
                SparkCore.LOGGER.info("API请求：加载模型 - path=${request.path}, entityId=${request.entityId}")

                val response = ServerThreading.callOnServerBlocking { srv ->
                    val level = srv.overworld()
                    val player = srv.playerList.players.firstOrNull()
                    if (player != null) {
                        ResourceApiService.loadModel(request, level, player)
                    } else ApiResponse.error("服务器没有在线玩家可作为上下文")
                } ?: ApiResponse.error("服务器环境不可用，无法执行模型加载")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                SparkCore.LOGGER.error("加载模型API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("加载模型请求处理失败", e)
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
    }
}

/**
 * 获取服务器世界（临时实现）
 */
private fun getServerLevel(): net.minecraft.server.level.ServerLevel? {
    return try {
        ServerThreading.callOnServerBlocking { it.overworld() }
    } catch (e: Exception) {
        SparkCore.LOGGER.debug("无法获取服务器世界: ${'$'}{e.message}")
        null
    }
}

/**
 * 获取服务器玩家（临时实现）
 */
private fun getServerPlayer(): net.minecraft.server.level.ServerPlayer? {
    return try {
        ServerThreading.callOnServerBlocking { it.playerList.players.firstOrNull() }
    } catch (e: Exception) {
        SparkCore.LOGGER.debug("无法获取服务器玩家: ${'$'}{e.message}")
        null
    }
}
