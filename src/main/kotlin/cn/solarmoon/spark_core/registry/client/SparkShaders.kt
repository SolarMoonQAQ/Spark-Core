package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.SparkCore
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterShadersEvent

object SparkShaders {

    @JvmStatic
    lateinit var DISTORT_SHADER: ShaderInstance
        private set

    @JvmStatic
    lateinit var STATIC_DISTORT: ShaderInstance
        private set

    @JvmStatic
    lateinit var H_CLIP: ShaderInstance
        private set

    private fun onRegisterShaders(event: RegisterShadersEvent) {
        event.registerShader(
            ShaderInstance(
                event.resourceProvider,
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "distort"),
                DefaultVertexFormat.POSITION_TEX
            )
        ) { DISTORT_SHADER = it }
        event.registerShader(
            ShaderInstance(
                event.resourceProvider,
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "static_distort"),
                DefaultVertexFormat.NEW_ENTITY
            )
        ) { STATIC_DISTORT = it }
        event.registerShader(
            ShaderInstance(
                event.resourceProvider,
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "hclip"),
                DefaultVertexFormat.NEW_ENTITY
            )
        ) { H_CLIP = it }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::onRegisterShaders)
    }

}