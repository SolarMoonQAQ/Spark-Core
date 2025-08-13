package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.SparkCore
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterShadersEvent

object SparkShaders {

    @JvmStatic
    var DISTORT_SHADER: ShaderInstance? = null
        private set

    private fun onRegisterShaders(event: RegisterShadersEvent) {
        event.registerShader(
            ShaderInstance(
                event.resourceProvider,
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "distort"),
                DefaultVertexFormat.POSITION_TEX
            )
        ) { DISTORT_SHADER = it }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::onRegisterShaders)
    }

}