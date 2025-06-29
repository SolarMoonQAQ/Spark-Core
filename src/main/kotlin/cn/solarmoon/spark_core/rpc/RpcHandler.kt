package cn.solarmoon.spark_core.rpc

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendAnimation
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

object RpcHandler : RpcService {
    override fun callMethod(methodName: String, params: Map<String, String>, level: ServerLevel, player: ServerPlayer): Any? {
        val entityId = params["entityId"]?.toIntOrNull() ?: player.id
        return when (methodName) {
            "loadModel" -> {
                val path = params["path"] ?: return null
                // 这里可以返回加载结果
                changeModel(path, entityId, level)
            }
            "playAnimation" -> {
                val animName = params["name"] ?: return null
                val transTime = params["transTime"]?.toIntOrNull() ?: 0
                playAnimation(animName, transTime, entityId, level)
            }
            "blendAnimations" -> {
                val anim1 = params["anim1"] ?: return null
                val anim2 = params["anim2"] ?: return null
                val weight = params["weight"]?.toDoubleOrNull() ?: 0.5
                blendAnimations(anim1, anim2, weight, entityId, level)
            }
            else -> null
        }
    }


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

    private fun playAnimation(animName: String, transTime: Int, entityId: Int, level: ServerLevel): Boolean {
        try {
            val animatable = level.getEntity(entityId) as? IAnimatable<*> ?: return false

            // 在服务器端设置动画
            val animInstance = AnimInstance.create(animatable, animName)
            animatable.animController.setAnimation(animInstance, transTime)

            //TODO: 同步到所有客户端
            SparkCore.LOGGER.info("服务器端为实体 $entityId 播放动画 $animName 成功")
            return true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("服务器端播放动画失败", e)
            return false
        }
    }

    private fun blendAnimations(anim1: String, anim2: String, weight: Double, entityId: Int?, level: ServerLevel): Boolean {
        try {
            if (entityId == null) return false
            val animatable = level.getEntity(entityId) as? IAnimatable<*> ?: return false

            // 在服务器端设置混合动画
            val animInstance1 = AnimInstance.create(animatable, anim1)
            val animInstance2 = AnimInstance.create(animatable, anim2)
            animatable.animController.blendAnimation("blendAnim1", BlendAnimation(animInstance1, weight))
            animatable.animController.blendAnimation("blendAnim2", BlendAnimation(animInstance2, weight))

            //TODO: 同步到所有客户端
            SparkCore.LOGGER.info("服务器端为实体 $entityId 混合动画 $anim1 和 $anim2 成功")
            return true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("服务器端混合动画失败", e)
            return false
        }
    }
}