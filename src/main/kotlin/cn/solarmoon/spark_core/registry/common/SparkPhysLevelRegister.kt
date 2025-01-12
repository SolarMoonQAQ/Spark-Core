package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysLevelRegisterEvent
import cn.solarmoon.spark_core.phys.thread.ClientPhysLevel
import cn.solarmoon.spark_core.phys.thread.ServerPhysLevel
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge

object SparkPhysLevelRegister {

    private fun reg(event: PhysLevelRegisterEvent) {
        val level = event.level
        if (!level.isClientSide)
            event.register(ServerPhysLevel(id("main"), "Physical Thread - Server", level as ServerLevel, 20, true))
        else
            event.register(ClientPhysLevel(id("main"), "Physical Thread - Client", level as ClientLevel, 20, true))
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}