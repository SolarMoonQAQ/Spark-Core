package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.mixin_interface.IClientLanguageMixin
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.fml.loading.FMLEnvironment
import java.io.ByteArrayInputStream

class LangModule : SparkPackModule {

    override val id: String = "lang"
    override val mode: ReadMode = ReadMode.CLIENT_LOCAL_ONLY
    private var count = 0
    private val totalTable: MutableMap<String, MutableMap<String, String>> = HashMap()
    private val totalComponentTable: MutableMap<String, MutableMap<String, Component>> = HashMap()

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (FMLEnvironment.dist.isClient && !fromServer) {
            count = 0
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

        SparkPackLoaderApplier.CLIENT_PACK.put(
            PackType.CLIENT_RESOURCES,
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            content
        )

        count++
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (FMLEnvironment.dist.isClient && !fromServer) {
            SparkCore.LOGGER.info("从外部包注册了 {} 个语言文件", count)
        }
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