package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.component.body_binder.BoxAroundHolderBinder
import cn.solarmoon.spark_core.skill.component.body_binder.BoxFollowAnimatedBoneBinder
import cn.solarmoon.spark_core.sync.IntSyncData
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object SparkCodeRegister {

    private fun reg(event: RegisterEvent) {
        event.register(SparkRegistries.RIGID_BODY_BINDER_CODEC.key(), id("box_follow_animated_bone")) { BoxFollowAnimatedBoneBinder.CODEC }
        event.register(SparkRegistries.RIGID_BODY_BINDER_CODEC.key(), id("box_around_holder")) { BoxAroundHolderBinder.CODEC }

        event.register(SparkRegistries.SYNC_DATA_STREAM_CODEC.key(), id("int")) { IntSyncData.STREAM_CODEC }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}