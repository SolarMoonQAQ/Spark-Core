package cn.solarmoon.spark_core.rpc

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

object RpcHandler : RpcService {
    override fun callMethod(
        methodName: String,
        params: Map<String, String>,
        level: ServerLevel,
        player: ServerPlayer
    ): Any? {
        TODO("Not yet implemented")
    }
//    override fun callMethod(methodName: String, params: Map<String, String>, level: ServerLevel, player: ServerPlayer): Any? {
//        val entityId = params["entityId"]?.toIntOrNull() ?: player.id
//        return when (methodName) {
//            "loadModel" -> {
//                val path = params["path"] ?: return null
//                // 这里可以返回加载结果
//                changeModel(path, entityId, level)
//            }
//            "playAnimation" -> {
//                val animName = params["name"] ?: return null
//                val transTime = params["transTime"]?.toIntOrNull() ?: 0
//                playAnimation(animName, transTime, entityId, level)
//            }
//            else -> null
//        }
//    }


    private fun changeModel(modelName: String, entityId: Int, level: ServerLevel): Boolean {
        try {
            val animatable = level.getEntity(entityId) as? IAnimatable<*> ?: return false
            val modelPath = ResourceLocation.parse(modelName)
            val entityName = modelName.substringAfter(":")
            val textureLocation = ResourceLocation.fromNamespaceAndPath("spark_core", "textures/entity/${entityName}.png")

            // 在服务器端更新模型信息
            val modelIndex = ModelIndex(modelPath, textureLocation)
            animatable.modelIndex = modelIndex

            //TODO: 同步到所有客户端
            SparkCore.LOGGER.info("服务器端更换实体 $entityId 的模型为 $modelName 成功")
            return true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("服务器端更换模型失败", e)
            return false
        }
    }

    private fun playAnimation(animName: String, entityId: Int, layerId: ResourceLocation, data: AnimLayerData, level: ServerLevel): Boolean {
        try {
            val animatable = level.getEntity(entityId) as? IAnimatable<*> ?: return false

            // 在服务器端设置动画
            val animInstance = AnimInstance.create(animatable, animName)
            animatable.animController.getLayer(layerId).setAnimation(animInstance, data)

            //TODO: 同步到所有客户端
            SparkCore.LOGGER.info("服务器端为实体 $entityId 播放动画 $animName 成功")
            return true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("服务器端播放动画失败", e)
            return false
        }
    }

}