package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.PhysLevelRegisterEvent
import cn.solarmoon.spark_core.phys.thread.ClientPhysLevel
import cn.solarmoon.spark_core.phys.thread.ServerPhysLevel
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge

object SparkPhysLevelRegister {

    private fun reg(event: PhysLevelRegisterEvent) {
        val level = event.level
        if (!level.isClientSide)
            event.register(ServerPhysLevel(SparkCore.MOD_ID, "Physical Thread - Server", level as ServerLevel, 20, true))
        else
            event.register(ClientPhysLevel(SparkCore.MOD_ID, "Physical Thread - Client", level as ClientLevel, 20, true))
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}