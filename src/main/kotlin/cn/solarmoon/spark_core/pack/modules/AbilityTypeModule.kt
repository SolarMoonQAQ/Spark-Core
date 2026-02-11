package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityType
import cn.solarmoon.spark_core.gas.AbilityTypeManager
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class AbilityTypeModule : SparkPackModule {

    override val id: String = "ability"

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (fromServer) AbilityTypeManager.initialize()
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean,
        fromServer: Boolean
    ) {
        if (!fromServer) return
        val res = ResourceLocation.fromNamespaceAndPath(namespace, fileName.removeSuffix(".json"))
        val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
        val serializer = AbilityType.Serializer.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
        AbilityTypeManager.apply {
            register(res, serializer.create())
        }
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        SparkCore.logger("技能加载器").info(buildString {
            appendLine("已加载 ${AbilityTypeManager.allAbilityTypes.size} 个技能:")
            appendLine(AbilityTypeManager.allAbilityTypes.keys.joinToString(", "))
        })
    }

}