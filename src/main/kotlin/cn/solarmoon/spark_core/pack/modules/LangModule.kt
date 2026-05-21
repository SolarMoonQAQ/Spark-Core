package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.gson.Gson
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
            // 第一步：将同 namespace:path 的多个内容包贡献按依赖顺序合并为一张完整表
            // 合并后得到 perLocationMerge: ResourceLocation -> 该语言文件的最终完整键值表
            val perLocationMerge: MutableMap<ResourceLocation, Map<String, String>> = HashMap()
            for ((location, contents) in collectedLangContents) {
                val merged = HashMap<String, String>()
                for (content in contents) {
                    val json = JsonParser.parseString(content.decodeToString()).asJsonObject
                    for ((key, value) in json.entrySet()) {
                        merged[key] = value.asString
                    }
                }
                perLocationMerge[location] = merged
            }

            // 从 perLocationMerge 中按语言代码分组，供第二步回退补足
            // location 格式为 namespace:lang/{langCode}.json，提取出 langCode
            val langCodeToLocation: MutableMap<String, ResourceLocation> = HashMap()
            val mergedTables: MutableMap<String, MutableMap<String, String>> = HashMap()
            for ((location, table) in perLocationMerge) {
                val code = langCodeFromLocation(location) ?: continue
                langCodeToLocation[code] = location
                mergedTables[code] = table as MutableMap<String, String>
            }

            // 第二步：对中文变体做互相补足，再用英文兜底
            buildChineseFallback(mergedTables)

            // 第三步：将合并后的表写回 CLIENT_PACK 的对应 location
            for ((code, table) in mergedTables) {
                val location = langCodeToLocation[code]!!
                val data = GSON.toJson(table).toByteArray()
                SparkPackLoaderApplier.CLIENT_PACK.put(PackType.CLIENT_RESOURCES, location, data)
            }

            SparkCore.LOGGER.info("从外部包注册了 {} 个语言文件", count)
        }
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

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private val CHINESE_CODES = listOf("zh_cn", "zh_tw", "zh_hk")
    }
}