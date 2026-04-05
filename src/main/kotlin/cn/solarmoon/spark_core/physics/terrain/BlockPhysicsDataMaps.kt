package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.registries.datamaps.DataMapType
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent
@EventBusSubscriber(modid = SparkCore.MOD_ID)
object BlockPhysicsDataMaps {

    @JvmField
    val BLOCK_PHYSICS_DATA: DataMapType<Block, BlockPhysicsData> = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "block_physics"),
        Registries.BLOCK,
        BlockPhysicsData.CODEC
    ).build()

    @SubscribeEvent
    fun register(event: RegisterDataMapTypesEvent) {
        event.register(BLOCK_PHYSICS_DATA)
    }

}