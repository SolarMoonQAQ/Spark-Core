package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkPackModuleRegister
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.ReloadableServerResources
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

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
class RecipeModule : SparkPackModule, SimplePreparableReloadListener<Unit>() {

    override val id: String = "recipes"
    var count = 0
    private val recipes: MutableList<Pair<ResourceLocation, JsonElement>> = ArrayList()

    companion object {
        @JvmStatic
        private var serverResources: ReloadableServerResources? = null

        @SubscribeEvent
        @JvmStatic
        fun registerServerReloadListeners(event: AddReloadListenerEvent) {
            event.addListener(SparkPackModuleRegister.recipe)
            serverResources = event.serverResources //每次reload都会有新的serverResources被创建，因此在这里保存下来最新的
        }
    }

    override fun onStart(isClientSide: Boolean) {
        count = 0
        recipes.clear()
        SparkCore.LOGGER.info("开始注入外部包配方…")
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        if (fileName.endsWith(".json")) {
            val nameSpace: String = if (pathSegments.isNotEmpty()) {
                pathSegments[0]
            } else {
                SparkCore.MOD_ID
            }
            val id = ResourceLocation.fromNamespaceAndPath(nameSpace, fileName.substringBeforeLast(".json"))
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            recipes.add(Pair.of(id, json))
            count++
        }
    }

    override fun onFinish(isClientSide: Boolean) {
        SparkCore.LOGGER.info("从外部包注入了{}个配方", count)
        if (serverResources != null && recipes.isNotEmpty()) {
            val recipeManager = serverResources!!.recipeManager
            registerRecipes(recipeManager, recipes)
        }
    }

    override fun prepare(resourceManager: ResourceManager, profiler: ProfilerFiller) {
        return
    }

    override fun apply(void: Unit, resourceManager: ResourceManager, profiler: ProfilerFiller) {
        if (serverResources != null && recipes.isNotEmpty()) {
            //注入自定义配方
            val recipeManager = serverResources!!.recipeManager
            registerRecipes(recipeManager, recipes)
        }
    }

    private fun registerRecipes(manager: RecipeManager, recipes: List<Pair<ResourceLocation, JsonElement>>) {
        // 使用反射访问RecipeManager内部映射
        try {
            // 获取当前不可变集合
            val byNameField = RecipeManager::class.java.getDeclaredField("byName")
            byNameField.isAccessible = true
            val byTypeField = RecipeManager::class.java.getDeclaredField("byType")
            byTypeField.isAccessible = true

            val currentByName =
                byNameField[manager] as Map<ResourceLocation, RecipeHolder<*>>
            val currentByType =
                byTypeField[manager] as Multimap<RecipeType<*>, RecipeHolder<*>>

            // 创建可变副本
            val newByName: MutableMap<ResourceLocation, RecipeHolder<*>> = HashMap(currentByName)
            val newByType: Multimap<RecipeType<*>, RecipeHolder<*>> = ArrayListMultimap.create()

            // 复制现有数据到新集合
            for ((key, value) in currentByType.asMap()) {
                newByType.putAll(key, value)
            }

            //添加新配方
            for (entry in recipes) {
                val id = entry.first
                val json = entry.second

                val recipeHolder = parseRecipe(id, json)
                if (recipeHolder != null) {
                    // 替换同名配方
                    newByName[id] = recipeHolder
                    // 按类型分组
                    val iterator = newByType[recipeHolder.value().type].iterator()
                    while (iterator.hasNext()) {
                        val value = iterator.next()
                        // 移除同名配方
                        if (value.id == id) {
                            iterator.remove()
                        }
                    }
                    newByType.put(recipeHolder.value().type, recipeHolder)
                }
            }

            // 创建不可变版本
            val immutableByName: Map<ResourceLocation, RecipeHolder<*>> = ImmutableMap.copyOf(newByName)
            val immutableByType: Multimap<RecipeType<*>, RecipeHolder<*>> = ImmutableMultimap.copyOf(newByType)

            // 更新RecipeManager
            byNameField[manager] = immutableByName
            byTypeField[manager] = immutableByType
        } catch (e: NoSuchFieldException) {
            throw RuntimeException("Failed to register external recipes", e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to register external recipes", e)
        }
    }

    private fun parseRecipe(id: ResourceLocation, json: JsonElement): RecipeHolder<*>? {
        try {
            // 解析配方对象
            val recipe = Recipe.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow { msg: String? ->
                JsonParseException(msg)
            }
            return RecipeHolder(id, recipe)
        } catch (e: java.lang.Exception) {
            // 输出解析错误
            SparkCore.LOGGER.error("An error occurred while reading recipe {}, skipped. Reason: {}", id, e.message)
            return null
        }
    }

}