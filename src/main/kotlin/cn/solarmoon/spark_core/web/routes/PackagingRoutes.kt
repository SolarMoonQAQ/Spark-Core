package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.PackagingTaskCreateRequestDto
import cn.solarmoon.spark_core.web.service.PackagingConfig
import cn.solarmoon.spark_core.web.service.PackagingManagementService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 打包管理路由配置
 */
fun Route.configurePackagingRoutes() {
    route("/packaging") {
        // 创建打包任务
        post("/tasks") {
            try {
                val request = call.receive<PackagingTaskCreateRequestDto>()
                val config = PackagingConfig(
                    resourceIds = request.config.resourceIds,
                    moduleIds = request.config.moduleIds,
                    includeDependencies = request.config.includeDependencies,
                    includeSoftDependencies = request.config.includeSoftDependencies,
                    outputPath = request.config.outputPath,
                    packageName = request.config.packageName,
                    includeMetadata = request.config.includeMetadata,
                    compressionLevel = request.config.compressionLevel,
                    executeImmediately = request.config.executeImmediately
                )

                val response = PackagingManagementService.createPackagingTask(config)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("创建打包任务API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("创建打包任务失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 执行打包任务
        post("/tasks/{taskId}/execute") {
            try {
                val taskId = call.parameters["taskId"] ?: throw IllegalArgumentException("缺少任务ID")
                val response = PackagingManagementService.executePackagingTask(taskId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("执行打包任务API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("执行打包任务失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 取消打包任务
        post("/tasks/{taskId}/cancel") {
            try {
                val taskId = call.parameters["taskId"] ?: throw IllegalArgumentException("缺少任务ID")
                val response = PackagingManagementService.cancelPackagingTask(taskId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("取消打包任务API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("取消打包任务失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取打包任务状态
        get("/tasks/{taskId}/status") {
            try {
                val taskId = call.parameters["taskId"] ?: throw IllegalArgumentException("缺少任务ID")
                val response = PackagingManagementService.getPackagingTaskStatus(taskId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务状态API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取打包任务状态失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取打包任务详情
        get("/tasks/{taskId}") {
            try {
                val taskId = call.parameters["taskId"] ?: throw IllegalArgumentException("缺少任务ID")
                val response = PackagingManagementService.getPackagingTaskDetails(taskId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务详情API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取打包任务详情失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取打包任务列表
        get("/tasks") {
            try {
                val status = call.request.queryParameters["status"]
                val response = PackagingManagementService.getPackagingTasks(status)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务列表API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取打包任务列表失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取打包任务历史
        get("/history") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val response = PackagingManagementService.getPackagingHistory(limit)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务历史API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取打包任务历史失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 设置自动打包启用状态
        post("/auto-packaging/enable") {
            try {
                val enabled = call.receive<Map<String, Boolean>>()["enabled"] ?: false
                val response = PackagingManagementService.setAutoPackagingEnabled(enabled)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("设置自动打包状态API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("设置自动打包状态失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取自动打包启用状态
        get("/auto-packaging/status") {
            try {
                val response = PackagingManagementService.getAutoPackagingEnabled()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取自动打包状态API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取自动打包状态失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取打包统计信息
        get("/stats") {
            try {
                val response = PackagingManagementService.getPackagingStats()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包统计信息API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取打包统计信息失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
