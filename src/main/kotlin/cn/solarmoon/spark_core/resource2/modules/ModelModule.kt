package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OLocator
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.util.div
import cn.solarmoon.spark_core.util.toRadians
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.phys.Vec3
import org.joml.Vector2i
import java.nio.charset.StandardCharsets
import kotlin.collections.set

class ModelModule: SparkPackModule {

    override val id: String = "models"

    override fun onStart(isClientSide: Boolean) {
        OModel.ORIGINS.clear()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        if (pathSegments.isEmpty()) throw IllegalArgumentException("模型的文件路径必须指向一个模型父名称（如：models/minecraft/player.json 指向名为 minecraft:player 的模型）")
        val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
        val target = json.asJsonObject.getAsJsonArray("minecraft:geometry").first().asJsonObject.getAsJsonArray("bones")
        // 单独读取贴图长宽
        val texture = json.asJsonObject.getAsJsonArray("minecraft:geometry").first().asJsonObject.getAsJsonObject("description")
        val coord = Vector2i(GsonHelper.getAsInt(texture, "texture_width"), GsonHelper.getAsInt(texture, "texture_height"))
        val bones = OBone.MAP_CODEC.decode(JsonOps.INSTANCE, target).orThrow.first.mapValues {
            val it = it.value
            // 应用长宽和修正到所有方块
            val cubes = it.cubes.map {
                OCube(
                    Vec3(-it.originPos.x - it.size.x, it.originPos.y, it.originPos.z).div(16.0),
                    it.size.div(16.0),
                    it.pivot.multiply(-1.0, 1.0, 1.0).div(16.0),
                    it.rotation.multiply(-1.0, -1.0, 1.0).toRadians(),
                    it.inflate.div(16),
                    it.uvUnion,
                    it.mirror,
                    coord.x,
                    coord.y
                )
            }.toMutableList()
            val locators = it.locators.mapValues { (_, value) ->
                OLocator(
                    value.offset.div(16.0).multiply(-1.0, 1.0, 1.0),
                    value.rotation.multiply(-1.0, -1.0, 1.0).div(16.0)
                )
            }
            OBone(
                it.name,
                it.parentName,
                it.pivot.multiply(-1.0, 1.0, 1.0).div(16.0),
                it.rotation.multiply(-1.0, -1.0, 1.0).toRadians(),
                LinkedHashMap(locators),
                ArrayList(cubes)
            )
        }
        val id = ResourceLocation.fromNamespaceAndPath(pathSegments[0], fileName.removeSuffix(".json"))
        OModel.ORIGINS[id] = OModel(coord.x, coord.y, LinkedHashMap(bones))
    }

    override fun onFinish(isClientSide: Boolean) {
        SparkCore.logger("模型加载器").info("\n\uD83D\uDEB6已加载模型\uD83D\uDEB6\n✅${OModel.ORIGINS.map { it.key }}\n")
    }

}