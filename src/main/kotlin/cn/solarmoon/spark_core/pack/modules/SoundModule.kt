package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.sound.SoundData
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

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if ((fromServer && isClientSide) || (!fromServer && !isClientSide)) return
        if (isClientSide) {
            sounds.clear()
            generatedSounds.clear()
            oggCount = 0
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
                SparkPackLoaderApplier.CLIENT_PACK.put(
                    PackType.CLIENT_RESOURCES,
                    ResourceLocation.fromNamespaceAndPath(namespace, fileName),
                    content
                )
            }
        }
    }


    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (FMLEnvironment.dist.isClient) {
            SparkCore.LOGGER.info("从外部包注册了{}种自定义音效资源", oggCount)
        }
    }

}