package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.*
import cn.solarmoon.spark_core.web.service.ResourceApiService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.neoforged.neoforge.server.ServerLifecycleHooks

/**
 * 动画控制路由配置
 */
fun Route.configureAnimationRoutes() {
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
                val errorResponse = RouteConfig.createErrorResponse("播放动画请求处理失败", e)
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
                val errorResponse = RouteConfig.createErrorResponse("替换状态动画请求处理失败", e)
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