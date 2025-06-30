package cn.solarmoon.spark_core.ik.origin

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SimpleJsonListener
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller

/**
 * 资源监听器，用于从JSON文件加载IK约束
 * 功能与EntityModelListener类似，但专用于IK约束
 */
class IKConstraintListener : SimpleJsonListener("geo/ik_constraints") {

    override fun apply(
        reads: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    ) {
        // 在加载新约束之前清除现有约束
        OIKConstraint.ORIGINS.clear()

        var loadedCount = 0

        reads.forEach { (id, jsonElement) ->
            try {
                // 解析约束JSON数组
                val constraintArray = jsonElement.asJsonArray

                // 遍历数组中的每个约束
                constraintArray.forEach { constraintJson ->
                    // 使用CODEC解码约束
                    val constraintResult = OIKConstraint.CODEC.decode(JsonOps.INSTANCE, constraintJson)
                        .getOrThrow()

                    // 将约束存储到ORIGINS映射中
                    OIKConstraint.ORIGINS[id] = constraintResult.first

                    loadedCount++
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("无法加载IK约束 ${id}: ${e.message}")
                e.printStackTrace()
            }
        }

        SparkCore.LOGGER.info("已加载 ${OIKConstraint.ORIGINS.size} 种类型的IK约束")
    }
}