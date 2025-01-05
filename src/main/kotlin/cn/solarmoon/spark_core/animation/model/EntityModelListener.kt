package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.model.part.BonePart
import cn.solarmoon.spark_core.animation.model.part.CubePart
import cn.solarmoon.spark_core.animation.model.part.Locator
import cn.solarmoon.spark_core.data.SimpleJsonListener
import cn.solarmoon.spark_core.phys.toRadians
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.GsonHelper
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.phys.Vec3
import org.joml.Vector2i
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

class EntityModelListener: SimpleJsonListener("geo/model") {

    override fun apply(
        reads: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    ) {
        reads.forEach { id, json ->
            val target = json.asJsonObject.getAsJsonArray("minecraft:geometry").first().asJsonObject.getAsJsonArray("bones")
            // 单独读取贴图长宽
            val texture = json.asJsonObject.getAsJsonArray("minecraft:geometry").first().asJsonObject.getAsJsonObject("description")
            val coord = Vector2i(GsonHelper.getAsInt(texture, "texture_width"), GsonHelper.getAsInt(texture, "texture_height"))
            val bones = BonePart.MAP_CODEC.decode(JsonOps.INSTANCE, target).orThrow.first.mapValues {
                val it = it.value
                // 应用长宽和修正到所有方块
                val cubes = it.cubes.map { CubePart(Vec3(- it.originPos.x - it.size.x, it.originPos.y, it.originPos.z).div(16.0), it.size.div(16.0), it.pivot.multiply(-1.0, 1.0, 1.0).div(16.0), it.rotation.multiply(-1.0, -1.0, 1.0).toRadians(), it.inflate.div(16), it.uvUnion, it.mirror, coord.x, coord.y) }.toMutableList()
                val locators = it.locators.mapValues { (_, value) -> Locator(value.offset.div(16.0), value.rotation.div(16.0)) }
                BonePart(it.name, it.parentName, it.pivot.multiply(-1.0, 1.0, 1.0).div(16.0), it.rotation.multiply(-1.0, -1.0, 1.0).toRadians(), LinkedHashMap(locators), ArrayList(cubes))
            }
            CommonModel.ORIGINS[id] = CommonModel(coord.x, coord.y, LinkedHashMap(bones))
        }
        SparkCore.LOGGER.info("已加载 ${CommonModel.ORIGINS.size} 种类型的模型")
    }

}