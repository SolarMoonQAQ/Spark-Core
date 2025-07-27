package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.*
import cn.solarmoon.spark_core.web.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 依赖管理路由配置
 */
fun Route.configureDependencyRoutes() {
    route("/dependencies") {
        // 获取依赖图
        get("/graph/{resourceId}") {
            try {
                val resourceId = call.parameters["resourceId"] ?: throw IllegalArgumentException("缺少资源ID")
                val includeIndirect = call.request.queryParameters["includeIndirect"]?.toBoolean() ?: true
                val hardOnly = call.request.queryParameters["hardOnly"]?.toBoolean() ?: false

                val response = DependencyEditService.getDependencyGraph(
                    resourceId = resourceId,
                    includeIndirect = includeIndirect,
                    hardOnly = hardOnly
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取依赖图API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取依赖图失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 添加依赖关系
        post("/add") {
            try {
                val request = call.receive<DependencyAddRequestDto>()
                val response = DependencyEditService.addDependency(
                    sourceId = request.sourceId,
                    targetId = request.targetId,
                    dependencyType = request.dependencyType,
                    validateFirst = request.validateFirst
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("添加依赖关系API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("添加依赖关系失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 移除依赖关系
        post("/remove") {
            try {
                val request = call.receive<DependencyRemoveRequestDto>()
                val response = DependencyEditService.removeDependency(
                    sourceId = request.sourceId,
                    targetId = request.targetId
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("移除依赖关系API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("移除依赖关系失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 验证依赖关系
        post("/validate") {
            try {
                val request = call.receive<DependencyValidationRequestDto>()
                val response = DependencyEditService.validateDependency(
                    sourceId = request.sourceId,
                    targetId = request.targetId
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("验证依赖关系API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("验证依赖关系失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 批量添加依赖关系
        post("/batch-add") {
            try {
                val request = call.receive<BatchDependencyRequestDto>()
                val operations = request.operations
                    .filter { it.type == "ADD" }
                    .map {
                        DependencyOperation(
                            sourceId = it.sourceId,
                            targetId = it.targetId,
                            dependencyType = it.dependencyType ?: "SOFT"
                        )
                    }

                val response = DependencyEditService.batchAddDependencies(operations)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("批量添加依赖关系API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("批量添加依赖关系失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 批量移除依赖关系
        post("/batch-remove") {
            try {
                val request = call.receive<BatchDependencyRequestDto>()
                val operations = request.operations
                    .filter { it.type == "REMOVE" }
                    .map {
                        DependencyRemoveOperation(
                            sourceId = it.sourceId,
                            targetId = it.targetId
                        )
                    }

                val response = DependencyEditService.batchRemoveDependencies(operations)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("批量移除依赖关系API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("批量移除依赖关系失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取依赖建议
        get("/suggestions/{resourceId}") {
            try {
                val resourceId = call.parameters["resourceId"] ?: throw IllegalArgumentException("缺少资源ID")
                val response = DependencyEditService.getDependencySuggestions(resourceId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取依赖建议API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取依赖建议失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
