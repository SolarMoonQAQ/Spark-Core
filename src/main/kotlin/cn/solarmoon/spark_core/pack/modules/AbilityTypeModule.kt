package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityType
import cn.solarmoon.spark_core.gas.AbilityTypeManager
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class AbilityTypeModule: SparkPackModule {

    override val id: String = "ability"

    override fun onStart(isClientSide: Boolean) {
        AbilityTypeManager.initialize()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        val res = ResourceLocation.fromNamespaceAndPath(pathSegments[0], fileName.removeSuffix(".json"))
        val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
        val serializer = AbilityType.Serializer.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
        AbilityTypeManager.apply {
            register(res, serializer.create())
        }
    }

    override fun onFinish(isClientSide: Boolean) {
        SparkCore.logger("技能加载器").info(buildString {
            appendLine("已加载 ${AbilityTypeManager.allAbilityTypes.size} 个技能:")
            appendLine(AbilityTypeManager.allAbilityTypes.keys.joinToString(", "))
        })
    }

}