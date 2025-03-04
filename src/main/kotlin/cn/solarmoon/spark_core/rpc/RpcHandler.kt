package cn.solarmoon.spark_core.rpc

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.BlendAnimation
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object RpcHandler : RpcService {
    override fun callMethod(methodName: String, params: Map<String, String>): Any? {
        val entityId = params["entityId"]?.toIntOrNull() ?: (Minecraft.getInstance().player?.id ?: 0)
        return when (methodName) {
            "loadModel" -> {
                val path = params["path"] ?: return null
                // 这里可以返回加载结果
                changeModel(path, entityId)
            }
            "playAnimation" -> {
                val animName = params["name"] ?: return null
                val transTime = params["transTime"]?.toIntOrNull() ?: 0
                playAnimation(animName, transTime, entityId)
            }
            "blendAnimations" -> {
                val anim1 = params["anim1"] ?: return null
                val anim2 = params["anim2"] ?: return null
                val weight = params["weight"]?.toDoubleOrNull() ?: 0.5
                blendAnimations(anim1, anim2, weight, entityId)
            }
            else -> null
        }
    }


    private fun changeModel(modelName: String, entityId: Int = 0): Boolean {
        val level = Minecraft.getInstance().level
        val animatable = level?.getEntity(entityId) as? IAnimatable<*> ?: return false
        val modelPath = ResourceLocation.parse("${modelName.substringBefore(":")}:${modelName.substringAfter(":")}")
        val entityName = modelName.substringAfter(":")
        val textureLocation = ResourceLocation.fromNamespaceAndPath("spark_core", "textures/entity/${entityName}.png")
        animatable.modelIndex.apply {
            this.modelPath = modelPath
            this.textureLocation = textureLocation
        }
        return true
    }

    private fun playAnimation(animName: String, transTime: Int, entityId: Int): Boolean {
        val level = Minecraft.getInstance().level
        val animatable = entityId.let { level?.getEntity(it) as? IAnimatable<*> } ?: return false
        val animInstance = AnimInstance.create(animatable, animName)
        animatable.animController.setAnimation(animInstance, transTime)
        return true
    }

    private fun blendAnimations(anim1: String, anim2: String, weight: Double, entityId: Int?): Boolean {
        val level = Minecraft.getInstance().level
        val animatable = entityId?.let { level?.getEntity(it) as? IAnimatable<*> } ?: return false
        val animInstance1 = AnimInstance.create(animatable, anim1)
        val animInstance2 = AnimInstance.create(animatable, anim2)
        animatable.animController.blendSpace.put("blendAnim1", BlendAnimation(animInstance1, weight))
        animatable.animController.blendSpace.put("blendAnim2", BlendAnimation(animInstance2, weight))
        return true
    }
} 