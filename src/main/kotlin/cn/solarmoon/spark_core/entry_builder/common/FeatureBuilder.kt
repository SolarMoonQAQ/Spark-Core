package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.core.Holder
import net.minecraft.core.HolderGetter
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.minecraft.world.level.levelgen.placement.PlacementModifier
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier


class FeatureBuilder<C: FeatureConfiguration, F: Feature<C>>(//
    private val modId: String,
    private val featureDeferredRegister: DeferredRegister<Feature<*>>
) {

    private var id = ""
    private var configSupplier: Supplier<C>? = null
    private var placementsSupplier: Supplier<List<PlacementModifier>>? = null
    private var featureSupplier: Supplier<F>? = null

    fun id(id: String) = apply { this.id = id }

    fun config(configSupplier: Supplier<C>) = apply { this.configSupplier = configSupplier }

    fun placements(placementsSupplier: Supplier<List<PlacementModifier>>) = apply { this.placementsSupplier = placementsSupplier }

    fun bound(featureSupplier: Supplier<F>) = apply { this.featureSupplier = featureSupplier }

    fun build(): FeatureEntry<C, F> {
        val entry = FeatureEntry(
            ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(modId, id)),
            ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(modId, id)),
            configSupplier!!,
            placementsSupplier!!,
            featureDeferredRegister.register(id, featureSupplier!!)
        )
        Gather.FEATURE_ENTRIES.add(entry)
        return entry
    }

    class FeatureEntry<C: FeatureConfiguration, F: Feature<C>>(
        val configKey: ResourceKey<ConfiguredFeature<*, *>>,
        val placementKey: ResourceKey<PlacedFeature>,
        val config: Supplier<C>,
        val placements: Supplier<List<PlacementModifier>>,
        val feature: DeferredHolder<Feature<*>, F>
    )

    object Gather {

        @JvmStatic
        val FEATURE_ENTRIES = mutableListOf<FeatureEntry<*, *>>()

        @JvmStatic
        fun <C: FeatureConfiguration, F: Feature<C>> configBootStrap(context: BootstrapContext<ConfiguredFeature<*, *>>) {
            @Suppress("UNCHECKED_CAST")
            FEATURE_ENTRIES.forEach {
                context.register(it.configKey, ConfiguredFeature(it.feature.get() as F, it.config.get() as C))
            }
        }

        @JvmStatic
        fun placedBootStrap(context: BootstrapContext<PlacedFeature>) {
            val featureGetter: HolderGetter<ConfiguredFeature<*, *>> = context.lookup(Registries.CONFIGURED_FEATURE)
            FEATURE_ENTRIES.forEach {
                val holder: Holder<ConfiguredFeature<*, *>> = featureGetter.getOrThrow(it.configKey)
                context.register(it.placementKey, PlacedFeature(holder, it.placements.get()))
            }
        }

    }

}