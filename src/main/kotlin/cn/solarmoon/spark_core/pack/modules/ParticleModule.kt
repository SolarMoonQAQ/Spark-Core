package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.particle.client.ParticleDefinitionLoader
import cn.solarmoon.spark_core.particle.client.ParticleEffectDeserializer
import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

/**
 * 粒子内容包模块。
 * 从 spark_modules/<pack>/<namespace>/particles/**/*.particle.json 加载粒子定义。
 */
class ParticleModule : SparkPackModule {

    override val id: String = "particles"
    override val mode: ReadMode = ReadMode.LOCAL_ONLY

    companion object {
        /** 加载的粒子定义缓存 */
        val definitions: MutableMap<ResourceLocation, ParticleEffectDefinition> = mutableMapOf()
    }

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && !fromServer) {
            definitions.clear()
        }
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: cn.solarmoon.spark_core.pack.graph.SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (!isClientSide) return
        if (!fileName.endsWith(".particle.json")) return

        val path = if (pathSegments.isEmpty()) {
            fileName.removeSuffix(".particle.json")
        } else {
            "${pathSegments.joinToString("/")}/${fileName.removeSuffix(".particle.json")}"
        }
        val id = ResourceLocation.fromNamespaceAndPath(namespace, path)

        try {
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            val def = ParticleEffectDeserializer.deserialize(id, json)
            definitions[id] = def
        } catch (e: Exception) {
            SparkCore.LOGGER.error("粒子定义 $id 加载失败: ${e.message}")
        }
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            SparkCore.LOGGER.info("已加载 ${definitions.size} 个粒子定义")
            // 注入到 ParticleDefinitionLoader 的注册表
            ParticleDefinitionLoader.getInstance().reload(definitions)
        }
    }
}
