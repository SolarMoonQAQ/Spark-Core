package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.event.ItemInHandModelRegisterEvent
import cn.solarmoon.spark_core.util.PerspectiveBakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModLoader
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.registries.RegisterEvent
import kotlin.collections.set

object SparkModelRegister {

    private val PERSPECTIVE_MODEL_LIST = mutableListOf<Pair<ModelResourceLocation, ModelResourceLocation>>()

    private fun reg(event: RegisterEvent) {
        if (event.registryKey.equals(Registries.ITEM)) {
            ModLoader.postEvent(ItemInHandModelRegisterEvent(PERSPECTIVE_MODEL_LIST))
        }
    }

    private fun onBaked(event: ModelEvent.BakingCompleted) {
        val models = event.modelBakery.bakedTopLevelModels
        PERSPECTIVE_MODEL_LIST.forEach {
            val newModel = PerspectiveBakedModel(models[it.first]!!, models[it.second]!!)
            models[it.first] = newModel
        }
    }

    private fun registerModels(event: ModelEvent.RegisterAdditional) {
        PERSPECTIVE_MODEL_LIST.forEach { event.register(it.second) }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::onBaked)
        bus.addListener(::registerModels)
        bus.addListener(::reg)
    }

}