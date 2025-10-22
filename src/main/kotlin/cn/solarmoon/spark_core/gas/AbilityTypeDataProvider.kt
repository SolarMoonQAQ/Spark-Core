package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.gas.AbilityType
import cn.solarmoon.spark_core.pack.SparkPackLoader
import com.mojang.serialization.JsonOps
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.CompletableFuture

abstract class AbilityTypeDataProvider(
    private val output: PackOutput,
    val packName: String,
    val modId: String
) : DataProvider {

    private val abilityTypes = mutableMapOf<ResourceLocation, AbilityType.Serializer>()
    private val prefixes = mutableMapOf<ResourceLocation, String>()

    override fun run(c: CachedOutput): CompletableFuture<*> {
        buildAbilities()
        val futures = mutableListOf<CompletableFuture<*>>()

        for ((id, graph) in abilityTypes) {
            val prefix = prefixes.getOrDefault(id, "")
            val path = output
                .outputFolder
                .resolve("${SparkPackLoader.MODULE_NAME}/$packName/ability/$modId/$prefix${id.path}.json")

            val json = AbilityType.Serializer.CODEC.encodeStart(JsonOps.INSTANCE, graph).orThrow

            futures += DataProvider.saveStable(c, json, path)
        }

        return CompletableFuture.allOf(*futures.toTypedArray())
    }

    override fun getName(): String = "Spark Core Ability"

    abstract fun buildAbilities()

    protected fun add(id: ResourceLocation, abilityType: AbilityType.Serializer, prefix: String = "") {
        abilityTypes[id] = abilityType
        prefixes[id] = prefix
    }

    protected fun id(id: String) = ResourceLocation.fromNamespaceAndPath(modId, id)

}