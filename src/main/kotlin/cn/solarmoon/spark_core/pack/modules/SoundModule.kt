package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.sound.SoundData
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.neoforged.fml.loading.FMLEnvironment
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class SoundModule : SparkPackModule {

    override val id: String = "sounds"
    override val mode: ReadMode = ReadMode.LOCAL_ONLY

    companion object {
        @JvmStatic
        val sounds: MutableMap<ResourceLocation, SoundData> = mutableMapOf()
        @JvmStatic
        val generatedSounds: ConcurrentMap<ResourceLocation, SoundData> = ConcurrentHashMap()

        private var oggCount = 0
        private var soundsJsonCount = 0

        @JvmStatic
        fun getSound(location: ResourceLocation): SoundData? {
            return sounds[location]?: generatedSounds[location]
        }

        /**
         * 动态注册声音(不会覆盖同名的资源包提供的声音)
         * @param location 声音注册名
         * @param soundData 声音数据
         */
        @JvmStatic
        fun registerGeneratedSound(location: ResourceLocation, soundData: SoundData) {
            generatedSounds[location] = soundData
        }
    }

    /** 收集阶段暂存：namespace:sounds.json -> 多个内容包提供的原始字节（按依赖顺序排列） */
    private val collectedSoundsJson: MutableMap<ResourceLocation, MutableList<ByteArray>> = HashMap()

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if ((fromServer && isClientSide) || (!fromServer && !isClientSide)) return
        if (isClientSide) {
            sounds.clear()
            generatedSounds.clear()
            oggCount = 0
            soundsJsonCount = 0
            collectedSoundsJson.clear()
            SparkCore.LOGGER.info("开始注册外部包自定义音效资源…")
        }
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (fromServer || !isClientSide) return
        when {
            fileName.endsWith(".ogg") -> {
                val fullPath = buildString {
                    append("sounds/")
                    if (pathSegments.isNotEmpty()) {
                        append(pathSegments.joinToString("/"))
                        append("/")
                    }
                    append(fileName)
                }
                SparkPackLoaderApplier.CLIENT_PACK.put(
                    PackType.CLIENT_RESOURCES,
                    ResourceLocation.fromNamespaceAndPath(namespace, fullPath),
                    content
                )
                val audioStream = JOrbisAudioStream(ByteArrayInputStream(content))
                val sound = SoundData(audioStream.readAll(), audioStream.getFormat())
                // 单独缓存一份
                sounds[ResourceLocation.fromNamespaceAndPath(namespace, fileName.removeSuffix(".ogg"))] = sound
                oggCount++
            }

            fileName == "sounds.json" -> {
                val location = ResourceLocation.fromNamespaceAndPath(namespace, fileName)
                // 先收集所有原始内容，在 onFinish 中跨包合并后统一注入
                // 避免不同内容包使用相同 namespace 时直接覆盖整个文件
                collectedSoundsJson.getOrPut(location) { ArrayList() }.add(content)
                soundsJsonCount++
            }
        }
    }


    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (FMLEnvironment.dist.isClient) {
            // 合并同 namespace:sounds.json 的多个内容包贡献
            // 合并策略：同名音效事件键后者覆盖前者，不同名键全部保留
            for ((location, contents) in collectedSoundsJson) {
                val merged = JsonObject()
                for (content in contents) {
                    val json = JsonParser.parseString(content.decodeToString()).asJsonObject
                    for ((key, value) in json.entrySet()) {
                        merged.add(key, value)
                    }
                }
                SparkPackLoaderApplier.CLIENT_PACK.put(
                    PackType.CLIENT_RESOURCES,
                    location,
                    merged.toString().toByteArray()
                )
            }
            SparkCore.LOGGER.info("从外部包注册了{}种自定义音效资源，{}个sounds.json", oggCount, soundsJsonCount)
        }
    }

}