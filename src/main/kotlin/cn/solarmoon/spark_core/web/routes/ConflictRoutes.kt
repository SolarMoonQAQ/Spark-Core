package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.BatchConflictResolutionRequestDto
import cn.solarmoon.spark_core.web.service.ConflictManagementService
import cn.solarmoon.spark_core.web.service.ConflictResolutionOperation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 冲突处理路由配置
 */
fun Route.configureConflictRoutes() {
    route("/conflicts") {
        // 获取所有冲突列表
        get("/") {
            try {
                val response = ConflictManagementService.getAllConflicts()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取冲突列表API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取冲突列表失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取特定模块的冲突
        get("/module/{moduleId}") {
            try {
                val moduleId = call.parameters["moduleId"] ?: throw IllegalArgumentException("缺少模块ID")
                val response = ConflictManagementService.getModuleConflicts(moduleId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取模块冲突列表API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取模块冲突列表失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取文件差异信息
        get("/{resourceId}/diff") {
            try {
                val resourceId = call.parameters["resourceId"] ?: throw IllegalArgumentException("缺少资源ID")
                val response = ConflictManagementService.getFileDiff(resourceId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取文件差异API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取文件差异失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 解决冲突 - 使用当前版本
        post("/{resourceId}/resolve/current") {
            try {
                val resourceId = call.parameters["resourceId"] ?: throw IllegalArgumentException("缺少资源ID")
                val response = ConflictManagementService.resolveConflictUseCurrent(resourceId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("解决冲突(使用当前版本)API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("解决冲突失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 解决冲突 - 恢复Legacy版本
        post("/{resourceId}/resolve/legacy") {
            try {
                val resourceId = call.parameters["resourceId"] ?: throw IllegalArgumentException("缺少资源ID")
                val response = ConflictManagementService.resolveConflictUseLegacy(resourceId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("解决冲突(恢复Legacy版本)API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("解决冲突失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 批量解决冲突
        post("/batch-resolve") {
            try {
                val request = call.receive<BatchConflictResolutionRequestDto>()
                val operations = request.operations.map {
                    ConflictResolutionOperation(
                        resourceId = it.resourceId,
                        resolution = it.resolution
                    )
                }

                val response = ConflictManagementService.batchResolveConflicts(operations)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("批量解决冲突API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("批量解决冲突失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取冲突统计信息
        get("/stats") {
            try {
                val response = ConflictManagementService.getConflictStats()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取冲突统计信息API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取冲突统计信息失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 清理Legacy文件夹
        post("/cleanup-legacy") {
            try {
                val response = ConflictManagementService.cleanupLegacyFolder()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("清理Legacy文件夹API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("清理Legacy文件夹失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
