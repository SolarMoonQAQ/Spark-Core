package cn.solarmoon.spark_core.web.routes

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.web.dto.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 传统资源路由配置
 * 包含基于注册表的资源查询功能
 */
fun Route.configureLegacyResourceRoutes() {
    route("/resources") {
        // 列出可用资源
        get("/list") {
            try {
                val resources = mutableMapOf<String, List<String>>()

                // 获取动画资源
                SparkRegistries.TYPED_ANIMATION.let { registry ->
                    val animations = mutableListOf<String>()
                    registry.keySet().forEach { location ->
                        animations.add(location.toString())
                    }
                    resources["animations"] = animations
                }

                // 获取模型资源
                SparkRegistries.MODELS.let { registry ->
                    val models = mutableListOf<String>()
                    registry.keySet().forEach { location ->
                        models.add(location.toString())
                    }
                    resources["models"] = models
                }

                // 获取纹理资源
                SparkRegistries.DYNAMIC_TEXTURES.let { registry ->
                    val textures = mutableListOf<String>()
                    registry.keySet().forEach { location ->
                        textures.add(location.toString())
                    }
                    resources["textures"] = textures
                }

                // 获取JS脚本资源
                SparkRegistries.JS_SCRIPTS.let { registry ->
                    val scripts = mutableListOf<String>()
                    registry.keySet().forEach { location ->
                        scripts.add(location.toString())
                    }
                    resources["scripts"] = scripts
                }

                // 获取IK组件资源
                SparkRegistries.IK_COMPONENT_TYPE.let { registry ->
                    val ikComponents = mutableListOf<String>()
                    registry.keySet().forEach { location ->
                        ikComponents.add(location.toString())
                    }
                    resources["ik_components"] = ikComponents
                }

                val response = ApiResponse.success(resources, "资源列表获取成功")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源列表API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取资源列表失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // 查看注册表状态
        get("/registries") {
            try {
                val registryStatus = mutableMapOf<String, Map<String, Any>>()

                // 检查各个注册表的状态
                SparkRegistries.TYPED_ANIMATION.let { registry ->
                    registryStatus["typed_animation"] = mapOf(
                        "total_entries" to registry.size(),
                        "registry_key" to registry.key().location().toString(),
                        "value_type" to "TypedAnimation",
                        "is_empty" to registry.isEmpty()
                    )
                }

                SparkRegistries.MODELS.let { registry ->
                    registryStatus["models"] = mapOf(
                        "total_entries" to registry.size(),
                        "registry_key" to registry.key().location().toString(),
                        "value_type" to "OModel",
                        "is_empty" to registry.isEmpty()
                    )
                }

                SparkRegistries.DYNAMIC_TEXTURES.let { registry ->
                    registryStatus["dynamic_textures"] = mapOf(
                        "total_entries" to registry.size(),
                        "registry_key" to registry.key().location().toString(),
                        "value_type" to "OTexture",
                        "is_empty" to registry.isEmpty()
                    )
                }

                SparkRegistries.JS_SCRIPTS.let { registry ->
                    registryStatus["js_scripts"] = mapOf(
                        "total_entries" to registry.size(),
                        "registry_key" to registry.key().location().toString(),
                        "value_type" to "OJSScript",
                        "is_empty" to registry.isEmpty()
                    )
                }

                SparkRegistries.IK_COMPONENT_TYPE.let { registry ->
                    registryStatus["ik_component_type"] = mapOf(
                        "total_entries" to registry.size(),
                        "registry_key" to registry.key().location().toString(),
                        "value_type" to "TypedIKComponent",
                        "is_empty" to registry.isEmpty()
                    )
                }

                val response = ApiResponse.success(registryStatus, "注册表状态获取成功")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取注册表状态API错误", e)
                val errorResponse = RouteConfig.createErrorResponse("获取注册表状态失败", e)
                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }
    }
}
