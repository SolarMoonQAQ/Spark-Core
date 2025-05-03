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
 * Resource listener that loads IK constraints from JSON files.
 * Similar to EntityModelListener but for IK constraints.
 */
class IKConstraintListener : SimpleJsonListener("geo/ik_constraints") {

    override fun apply(
        reads: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    ) {
        // Clear existing constraints before loading new ones
        OIKConstraint.ORIGINS.clear()

        var loadedCount = 0

        reads.forEach { (id, jsonElement) ->
            try {
                // Parse the JSON array of constraints
                val constraintArray = jsonElement.asJsonArray

                // For each constraint in the array
                constraintArray.forEach { constraintJson ->
                    // Decode the constraint using the CODEC
                    val constraintResult = OIKConstraint.CODEC.decode(JsonOps.INSTANCE, constraintJson)
                        .getOrThrow()

                    // Store the constraint in the ORIGINS map
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
