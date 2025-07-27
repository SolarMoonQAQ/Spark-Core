package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.dto.ResourceSearchRequestDto
import cn.solarmoon.spark_core.web.service.ResourceBrowserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 资源管理路由配置
 */
fun Route.configureResourceManagementRoutes() {
    route("/resources") {
        // 获取资源树
        get("/tree") {
            try {
                val refresh = call.request.queryParameters["refresh"]?.toBoolean() ?: false
                val response = ResourceBrowserService.getResourceTree(refresh)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源树API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取资源树失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 搜索资源
        post("/search") {
            try {
                val request = call.receive<ResourceSearchRequestDto>()
                val response = ResourceBrowserService.searchResources(
                    query = request.query,
                    namespace = request.filters.namespace,
                    modId = request.filters.modId,
                    moduleName = request.filters.moduleName,
                    resourceType = request.filters.resourceType,
                    tags = request.filters.tags
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("搜索资源API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("搜索资源失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取资源详情
        get("/{resourceId}") {
            try {
                val resourceId = call.parameters["resourceId"] ?: throw IllegalArgumentException("缺少资源ID")
                val response = ResourceBrowserService.getResourceDetails(resourceId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源详情API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取资源详情失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取资源状态
        get("/{resourceId}/status") {
            try {
                val resourceId = call.parameters["resourceId"] ?: throw IllegalArgumentException("缺少资源ID")
                val response = ResourceBrowserService.getResourceStatus(resourceId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源状态API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取资源状态失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 获取资源统计信息
        get("/stats") {
            try {
                val response = ResourceBrowserService.getResourceStats()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源统计信息API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取资源统计信息失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
