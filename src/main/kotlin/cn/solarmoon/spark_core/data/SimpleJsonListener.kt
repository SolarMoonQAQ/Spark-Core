package cn.solarmoon.spark_core.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller

abstract class SimpleJsonListener(fileName: String): SimpleJsonResourceReloadListener(Gson().newBuilder().create(), fileName) {//

    abstract override fun apply(
        reads: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    )

}