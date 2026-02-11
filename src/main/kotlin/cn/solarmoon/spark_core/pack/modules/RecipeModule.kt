package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.registry.common.SparkPackModuleRegister
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSerializer
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.ReloadableServerResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.AddReloadListenerEvent
import java.nio.charset.StandardCharsets

class RecipeModule : SparkPackModule {

    override val id: String = "recipe"
    var count = 0
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (fromServer && !isClientSide) {
            count = 0
            SparkCore.LOGGER.info("开始注入外部包配方…")
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
        if (!fromServer || isClientSide) return
        if (!fileName.endsWith(".json")) return
        val path = if (pathSegments.isEmpty()) {
            "recipe/$fileName"
        } else {
            "recipe/${pathSegments.joinToString("/")}/$fileName"
        }

        val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
        val cleanJson = SparkPackModule.gson.toJson(json) // 返回无注释的 JSON 字符串
        SparkPackLoaderApplier.DATA_PACK.put(
            PackType.SERVER_DATA,
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            cleanJson.toByteArray()
        )
        count++
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!fromServer || isClientSide) return
        SparkCore.LOGGER.info("从外部包注入了{}个配方", count)
    }

}