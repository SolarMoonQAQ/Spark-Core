package cn.solarmoon.spark_core.event

import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

class ItemInHandModelRegisterEvent(
    private val perspectiveModels: MutableList<Pair<ModelResourceLocation, ModelResourceLocation>>
): Event(), IModBusEvent {

    fun addInHandModel(item: Item) {
        val res = BuiltInRegistries.ITEM.getKey(item)
        val rawName = ModelResourceLocation.inventory(res)
        val inHandName = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(res.namespace, "item/${res.path}_in_hand"))
        perspectiveModels.add(Pair(rawName, inHandName))
    }

}