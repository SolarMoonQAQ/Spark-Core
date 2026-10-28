package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.neoforged.fml.loading.FMLEnvironment

class LangModule : SparkPackModule {

    override val id: String = "lang"
    override val mode: ReadMode = ReadMode.CLIENT_LOCAL_ONLY
    private var count = 0

    /** 收集阶段暂存：namespace:lang/langCode.json -> 多个内容包提供的原始字节（按依赖顺序排列） */
    private val collectedLangContents: MutableMap<ResourceLocation, MutableList<ByteArray>> = HashMap()

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (FMLEnvironment.dist.isClient && !fromServer) {
            count = 0
            collectedLangContents.clear()
            SparkCore.LOGGER.info("开始加载外部包语言资源…")
        }
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
        if (fromServer) return
        if (!FMLEnvironment.dist.isClient) return
        if (!fileName.endsWith(".json")) return

        val langCode = fileName.substringBeforeLast(".json")
        val path = "lang/$langCode.json"
        val location = ResourceLocation.fromNamespaceAndPath(namespace, path)

        // 先收集所有原始内容，在 onFinish 中跨包合并后统一注入
        // 避免不同内容包使用相同 namespace 时直接覆盖整个文件
        collectedLangContents.getOrPut(location) { ArrayList() }.add(content)

        count++
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (FMLEnvironment.dist.isClient && !fromServer) {
            // 合并同一语言文件的多个包贡献 → 按命名空间做语言回退 → 序列化
            val langFiles = buildLangFiles(collectedLangContents)
            for ((location, data) in langFiles) {
                SparkPackLoaderApplier.CLIENT_PACK.put(PackType.CLIENT_RESOURCES, location, data)
            }

            SparkCore.LOGGER.info(
                "从外部包读取了 {} 个语言文件，按命名空间合并后写出 {} 个",
                count,
                langFiles.size
            )
        }
    }

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private val CHINESE_CODES = listOf("zh_cn", "zh_tw", "zh_hk")

        /**
         * 将同 namespace:path 的多个内容包贡献按读取（依赖）顺序合并成一张完整表。
         * 返回 ResourceLocation -> 该语言文件的完整键值表，各命名空间互不干扰。
         */
        internal fun mergeCollectedContents(
            collected: Map<ResourceLocation, List<ByteArray>>
        ): MutableMap<ResourceLocation, MutableMap<String, String>> {
            val perLocationMerge: MutableMap<ResourceLocation, MutableMap<String, String>> = HashMap()
            for ((location, contents) in collected) {
                val merged = HashMap<String, String>()
                for (content in contents) {
                    val json = JsonParser.parseString(content.decodeToString()).asJsonObject
                    for ((key, value) in json.entrySet()) {
                        merged[key] = value.asString
                    }
                }
                perLocationMerge[location] = merged
            }
            return perLocationMerge
        }

        /**
         * 语言回退（zh_cn / zh_tw / zh_hk 互相补足，再用 en_us 兜底）只在同一命名空间内进行。
         * 不能按 langCode 跨命名空间分组：moji、t90a、m1a2_sep 都会提供 lang/zh_cn.json，
         * 以 langCode 为全局键会让后一个覆盖前一个，导致除一个命名空间外其余语言文件全部丢失。
         */
        internal fun fillFallbackPerNamespace(
            perLocationMerge: MutableMap<ResourceLocation, MutableMap<String, String>>
        ) {
            for (locations in perLocationMerge.keys.groupBy { it.namespace }.values) {
                val tables: MutableMap<String, MutableMap<String, String>> = HashMap()
                for (location in locations) {
                    val code = langCodeFromLocation(location) ?: continue
                    tables[code] = perLocationMerge.getValue(location)
                }
                buildChineseFallback(tables)
            }
        }

        /**
         * 合并 + 回退 + 序列化，返回每个语言文件最终写入虚拟资源包的内容。
         * 键是完整的 namespace:lang/xx.json，因此不同命名空间不会互相顶掉。
         */
        internal fun buildLangFiles(
            collected: Map<ResourceLocation, List<ByteArray>>
        ): Map<ResourceLocation, ByteArray> {
            val perLocationMerge = mergeCollectedContents(collected)
            fillFallbackPerNamespace(perLocationMerge)
            return perLocationMerge.mapValues { (_, table) -> GSON.toJson(table).toByteArray() }
        }

        /**
         * 对 zh_cn / zh_tw / zh_hk 各自做互相补足，再用英文兜底。
         * 补足后的结果直接写回对应语言的合并表，不依赖当前选中的语言。
         * 这样玩家在游戏中随时切换语言都能看到完整翻译。
         */
        private fun buildChineseFallback(tables: MutableMap<String, MutableMap<String, String>>) {
            for (code in CHINESE_CODES) {
                val main = tables[code] ?: continue
                for (fallbackCode in chineseFallbackChain(code)) {
                    val fallbackTable = tables[fallbackCode] ?: continue
                    for ((key, value) in fallbackTable) {
                        main.putIfAbsent(key, value)
                    }
                }
                val enTable = tables["en_us"] ?: continue
                for ((key, value) in enTable) {
                    main.putIfAbsent(key, value)
                }
            }
        }

        /** 中文变体回退优先级链 */
        private fun chineseFallbackChain(code: String): List<String> = when (code) {
            "zh_cn" -> listOf("zh_tw", "zh_hk")
            "zh_tw" -> listOf("zh_hk", "zh_cn")
            "zh_hk" -> listOf("zh_tw", "zh_cn")
            else    -> emptyList()
        }

        /** 从 ResourceLocation（lang/xx.json）中提取语言代码 xx */
        private fun langCodeFromLocation(location: ResourceLocation): String? {
            val path = location.path
            if (!path.startsWith("lang/") || !path.endsWith(".json")) return null
            return path.substringAfter("lang/").substringBeforeLast(".json")
        }
    }
}