package cn.solarmoon.spark_core.resource.autoregistry

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.handler.IDynamicResourceHandler
import cn.solarmoon.spark_core.resource.handler.DynamicAnimationHandler
import cn.solarmoon.spark_core.resource.handler.DynamicModelHandler
import cn.solarmoon.spark_core.resource.handler.DynamicTextureHandler
import cn.solarmoon.spark_core.resource.handler.DynamicJavaScriptHandler
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.registries.NewRegistryEvent

@EventBusSubscriber(modid = SparkCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object HandlerDiscoveryService {

    private val discoveredHandlers = mutableListOf<IDynamicResourceHandler>()

    private fun findHandlerImplementations(): List<IDynamicResourceHandler> {
        return listOf(
            DynamicAnimationHandler(SparkRegistries.TYPED_ANIMATION),
            DynamicModelHandler(SparkRegistries.MODELS),
            DynamicTextureHandler(SparkRegistries.DYNAMIC_TEXTURES),
            DynamicJavaScriptHandler(SparkRegistries.JS_SCRIPTS)
        )
    }

    @SubscribeEvent
    @JvmStatic
    fun onNewRegistry(event: NewRegistryEvent) {
        SparkCore.LOGGER.info("HandlerDiscoveryService: NewRegistryEvent received.")
    }

    fun discoverAndInitializeHandlers(modMainClass: Class<*>) {
        if (discoveredHandlers.isNotEmpty()) {
            SparkCore.LOGGER.info("Handlers already discovered. Skipping rediscovery.")
        }

        val handlers = findHandlerImplementations()
        if (discoveredHandlers.isEmpty()) { 
            discoveredHandlers.addAll(handlers)
        } else {
        }
        
        SparkCore.LOGGER.info("Discovered/Using {} dynamic resource handler(s).", handlers.size) 

        for (handler in discoveredHandlers) { 
            SparkCore.LOGGER.info("Initializing resources for handler: {}", handler::class.simpleName)
            try {
                val success = handler.initializeDefaultResources(modMainClass)
                if (!success) {
                    SparkCore.LOGGER.error(
                        "Failed to initialize default resources for handler: {}. Check handler logs for details.",
                        handler::class.simpleName
                    )
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error(
                    "Exception during resource initialization for handler: {}: {}",
                    handler::class.simpleName,
                    e.message,
                    e
                )
            }
        }
        SparkCore.LOGGER.info("All discovered handlers processed for resource initialization.")
    }

    fun getDiscoveredHandlers(): List<IDynamicResourceHandler> = discoveredHandlers.toList()
}
