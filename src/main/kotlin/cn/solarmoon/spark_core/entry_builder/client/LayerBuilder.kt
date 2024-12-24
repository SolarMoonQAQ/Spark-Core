package cn.solarmoon.spark_core.entry_builder.client

import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions
import java.util.function.Supplier


class LayerBuilder(private val modId: String, private val bus: IEventBus) {

    private var id = ""
    private var layerId = "main"
    private var layerSupplier: Supplier<LayerDefinition>? = null

    fun id(id: String) = apply { this.id = id }

    fun layerId(layerId: String) = apply { this.layerId = layerId }

    fun bound(layerSupplier: Supplier<LayerDefinition>) = apply { this.layerSupplier = layerSupplier }

    fun build(): ModelLayerLocation {
        val layer = ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(modId, id), layerId)
        bus.addListener { e: RegisterLayerDefinitions -> initLayers(layer, e) }
        return layer
    }

    private fun initLayers(layer: ModelLayerLocation, event: RegisterLayerDefinitions) {
        event.registerLayerDefinition(layer, layerSupplier!!)
    }

}