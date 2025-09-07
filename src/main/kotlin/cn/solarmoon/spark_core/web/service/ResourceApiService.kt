package cn.solarmoon.spark_core.web.service

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.anim.play.layer.DefaultLayer
import cn.solarmoon.spark_core.web.dto.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/**
 * 资源API服务类
 * 重构自RpcHandler，提供RESTful API友好的接口
 * 保持核心业务逻辑不变，调整接口以适配DTO对象和ApiResponse格式
 */
object ResourceApiService {

    /**
     * 加载模型
     * @param request 模型加载请求
     * @param level 服务器世界
     * @param player 请求的玩家
     * @return API响应，包含操作结果
     */
    fun loadModel(request: ModelLoadRequest, level: ServerLevel, player: ServerPlayer): ApiResponse<Boolean> {
        return try {
            val entityId = request.entityId ?: player.id
            val result = changeModel(request.path, entityId, level)
            if (result) {
                ApiResponse.success(true, "模型加载成功")
            } else {
                ApiResponse.error("模型加载失败：实体不存在或不支持动画")
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("API调用失败：loadModel", e)
            ApiResponse.error("模型加载失败：${e.message}")
        }
    }

    /**
     * 播放动画
     * @param request 动画播放请求
     * @param level 服务器世界
     * @param player 请求的玩家
     * @return API响应，包含操作结果
     */
    fun playAnimation(request: AnimationPlayRequest, level: ServerLevel, player: ServerPlayer): ApiResponse<Boolean> {
        return try {
            val entityId = request.entityId ?: player.id
            val result = playAnimation(request.name, entityId, DefaultLayer.MAIN_LAYER, AnimLayerData(enterTransitionTime = request.transTime), level)
            if (result) {
                ApiResponse.success(true, "动画播放成功")
            } else {
                ApiResponse.error("动画播放失败：实体不存在或不支持动画")
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("API调用失败：playAnimation", e)
            ApiResponse.error("动画播放失败：${e.message}")
        }
    }

    /**
     * 替换状态动画
     * @param request 状态动画替换请求
     * @param level 服务器世界
     * @param player 请求的玩家
     * @return API响应，包含操作结果
     */
    fun replaceStateAnimation(request: AnimationReplaceStateRequest, level: ServerLevel, player: ServerPlayer): ApiResponse<Boolean> {
        return try {
            // 注意：这个功能在原RpcHandler中没有实现，这里提供一个基础实现
            // 实际的状态动画替换逻辑可能需要根据具体需求进一步实现
            SparkCore.LOGGER.info("状态动画替换请求：state=${request.state}, animation=${request.animation}, entityId=${request.entityId}")
            ApiResponse.success(true, "状态动画替换功能待实现")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("API调用失败：replaceStateAnimation", e)
            ApiResponse.error("状态动画替换失败：${e.message}")
        }
    }

    // ==================== 私有方法：复用RpcHandler的核心业务逻辑 ====================

    /**
     * 更换模型（复用自RpcHandler.changeModel）
     */
    private fun changeModel(modelName: String, entityId: Int, level: ServerLevel): Boolean {
        try {
            val animatable = level.getEntity(entityId) as? IAnimatable<*> ?: return false
            val modelPath = ResourceLocation.parse(modelName)
            val entityName = modelName.substringAfter(":")
            val textureLocation = ResourceLocation.fromNamespaceAndPath("spark_core", "textures/entity/${entityName}.png")

            // 在服务器端更新模型信息
            val modelIndex = ModelIndex(modelPath, textureLocation)
            animatable.modelController.setModel(modelIndex)
            // TODO: 模型校验
            SparkCore.LOGGER.info("服务器端更换实体 $entityId 的模型为 $modelName 成功")
            return true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("服务器端更换模型失败", e)
            return false
        }
    }

    /**
     * 播放动画（复用自RpcHandler.playAnimation）
     */
    private fun playAnimation(animName: String, entityId: Int, layerId: ResourceLocation, data: AnimLayerData, level: ServerLevel): Boolean {
        try {
            val animatable = level.getEntity(entityId) as? IAnimatable<*> ?: return false

            // 在服务器端设置动画
            val animInstance = AnimInstance.create(animatable, animName)
            animatable.animController.getLayer(layerId).setAnimation(animInstance, data)

            SparkCore.LOGGER.info("服务器端为实体 $entityId 播放动画 $animName 成功")
            return true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("服务器端播放动画失败", e)
            return false
        }
    }

}
