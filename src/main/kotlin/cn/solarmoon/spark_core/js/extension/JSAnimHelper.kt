package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import net.minecraft.resources.ResourceLocation

object JSAnimHelper: JSComponent() {


    /**
     * 使用SparkResourcePathBuilder创建ModelIndex
     *
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityPath 实体路径
     * @return ModelIndex实例
     */
    fun createModelIndexWithBuilder(modId: String, moduleName: String, entityPath: String): ModelIndex {
        val modelPath = SparkResourcePathBuilder.buildModelPath(modId, moduleName, entityPath)
        val texturePath = SparkResourcePathBuilder.buildTexturePath(modId, moduleName, "entity/$entityPath")
        return ModelIndex(modelPath, texturePath)
    }

}