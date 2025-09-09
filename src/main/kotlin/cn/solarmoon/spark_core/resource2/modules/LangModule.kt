package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.mixin_interface.IClientLanguageMixin
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.fml.loading.FMLEnvironment
import java.io.ByteArrayInputStream

class LangModule : SparkPackModule, SimplePreparableReloadListener<Unit>() {

    override val id: String = "lang"
    var count = 0
    private val totalTable: MutableMap<String, MutableMap<String, String>> = HashMap()
    private val totalComponentTable: MutableMap<String, MutableMap<String, Component>> = HashMap()

    override fun onStart() {
        if (FMLEnvironment.dist.isClient) {
            count = 0
            SparkCore.LOGGER.info("开始注入外部包翻译文本…")
        }
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage
    ) {
        if (FMLEnvironment.dist.isClient && fileName.endsWith(".json")) {
            val lang = fileName.substringBeforeLast(".")
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                SparkCore.MOD_ID
            }
            val table = HashMap<String, String>()
            val componentTable = HashMap<String, Component>()
            val stream = ByteArrayInputStream(content)
            try {
                // 读取翻译表
                Language.loadFromJson(
                    stream,
                    { k, v -> table[k] = v },
                    { k, v -> componentTable[k] = v })
            } catch (e: Exception) {
                SparkCore.LOGGER.warn("Skipped language file: {}:{}", nameSpace, fileName, e)
            }
            // 将读取到的翻译表按照不同语言分别加入翻译总表
            totalTable.getOrPut(lang) { HashMap() }.putAll(table)
            totalComponentTable.getOrPut(lang) { HashMap() }.putAll(componentTable)
            count++
        }
    }

    override fun onFinish() {
        if (FMLEnvironment.dist.isClient) {
            SparkCore.LOGGER.info("从外部包注入了{}种，共{}条翻译文本", count, totalTable.values.sumOf { it.size })
            processAndAddTranslations()
        }
    }

    override fun prepare(resourceManager: ResourceManager, profiler: ProfilerFiller) {
        return
    }

    override fun apply(void: Unit, resourceManager: ResourceManager, profiler: ProfilerFiller) {
        processAndAddTranslations()
    }

    private fun processAndAddTranslations() {
        if (FMLEnvironment.dist.isClient) {
            val selected = Minecraft.getInstance().languageManager.selected
            val lang = Language.getInstance() as IClientLanguageMixin
            var table = totalTable[selected] ?: HashMap()
            var componentTable = totalComponentTable[selected] ?: HashMap()
            var merge: Pair<Map<String, String>, Map<String, Component>>
            //使用中文时，用简体翻译补足繁体翻译，或反过来
            when (selected) {
                "zh_cn" -> {
                    merge = mergeTranslations(table, componentTable, "zh_tw")
                    table = merge.first as MutableMap<String, String>
                    componentTable = merge.second as MutableMap<String, Component>
                    merge = mergeTranslations(table, componentTable, "zh_hk")
                    table = merge.first as MutableMap<String, String>
                    componentTable = merge.second as MutableMap<String, Component>
                }
                "zh_tw" -> {
                    merge = mergeTranslations(table, componentTable, "zh_hk")
                    table = merge.first as MutableMap<String, String>
                    componentTable = merge.second as MutableMap<String, Component>
                    merge = mergeTranslations(table, componentTable, "zh_cn")
                    table = merge.first as MutableMap<String, String>
                    componentTable = merge.second as MutableMap<String, Component>
                }
                "zh_hk" -> {
                    merge = mergeTranslations(table, componentTable, "zh_tw")
                    table = merge.first as MutableMap<String, String>
                    componentTable = merge.second as MutableMap<String, Component>
                    merge = mergeTranslations(table, componentTable, "zh_cn")
                    table = merge.first as MutableMap<String, String>
                    componentTable = merge.second as MutableMap<String, Component>
                }
            }
            //用英文翻译填充缺失的翻译键
            merge = mergeTranslations(
                table,
                componentTable,
                "en_us"
            )
            table = merge.first as MutableMap<String, String>
            componentTable = merge.second as MutableMap<String, Component>
            lang.`spark_core$addExtraStorage`(table)
            lang.`spark_core$addExtraComponentStorage`(componentTable)
        }
    }

    private fun mergeTranslations(
        table: Map<String, String>,
        componentTable: Map<String, Component>,
        backUpLangCode: String
    ): Pair<Map<String, String>, Map<String, Component>> {
        if (!totalTable.containsKey(backUpLangCode)) {
            return Pair(table, componentTable)
        }
        val enUsTable = totalTable[backUpLangCode] ?: HashMap()
        val enUsComponentTable = totalComponentTable[backUpLangCode] ?: HashMap()

        val completedTable = HashMap<String, String>()
        val completedComponentTable = HashMap<String, Component>()

        completedTable.putAll(enUsTable)
        completedComponentTable.putAll(enUsComponentTable)
        completedTable.putAll(table)
        completedComponentTable.putAll(componentTable)

        return Pair(completedTable, completedComponentTable)
    }

}