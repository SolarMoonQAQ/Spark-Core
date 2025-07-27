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
        val server = ServerLifecycleHooks.getCurrentServer()
        server?.overworld()
    } catch (e: Exception) {
        SparkCore.LOGGER.debug("无法获取服务器世界: ${e.message}")
        null
    }
}

/**
 * 获取服务器玩家（临时实现）
 */
private fun getServerPlayer(): net.minecraft.server.level.ServerPlayer? {
    return try {
        val server = ServerLifecycleHooks.getCurrentServer()
        val players = server?.playerList?.players
        players?.firstOrNull()
    } catch (e: Exception) {
        SparkCore.LOGGER.debug("无法获取服务器玩家: ${e.message}")
        null
    }
}
