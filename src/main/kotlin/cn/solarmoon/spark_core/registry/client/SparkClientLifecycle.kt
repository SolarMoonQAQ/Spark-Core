package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.dynamic.DynamicIdManager
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent

/**
 * 客户端生命周期钩子，用于在切换服务器/世界时（例如，从多人游戏到单人游戏）保持动态注册表和ID映射的整洁。
 */
object SparkClientLifecycle {

    /**
     * 清理所有动态注册表的公共方法
     */
    private fun clearAllDynamicRegistries(): Int {
        var registryClearSuccessCount = 0
        try {
            SparkRegistries.TYPED_ANIMATION.clearDynamic()
            registryClearSuccessCount++
            SparkRegistries.MODELS.clearDynamic()
            registryClearSuccessCount++
            SparkRegistries.DYNAMIC_TEXTURES.clearDynamic()
            registryClearSuccessCount++
//            SparkRegistries.JS_SCRIPTS.clearDynamic()
//            registryClearSuccessCount++
            SparkRegistries.IK_COMPONENT_TYPE.clearDynamic()
            registryClearSuccessCount++
        } catch (e: Exception) {
            SparkCore.LOGGER.error("清理动态注册表失败 (已成功清理 {} 个)", registryClearSuccessCount, e)
        }
        return registryClearSuccessCount
    }

    /**
     * 清理ID映射的公共方法
     */
    private fun clearAllIdMappings(): Boolean {
        return try {
            DynamicIdManager.clearAllForClient()
            true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("清理ID映射失败", e)
            false
        }
    }

    @SubscribeEvent
    fun onClientLoggingOut(@Suppress("UNUSED_PARAMETER") event: ClientPlayerNetworkEvent.LoggingOut) {
        SparkCore.LOGGER.info("客户端登出：开始清理动态注册表和ID映射")
        
        // 使用公共方法清理
        val registryCount = clearAllDynamicRegistries()
        val idMappingSuccess = clearAllIdMappings()
        cn.solarmoon.spark_core.animation.sync.AnimationSyncState.reset()
        
        // 如果主清理方法失败，尝试降级方案
        if (!idMappingSuccess) {
            try {
                SparkCore.LOGGER.info("尝试降级方案：逐个清理注册表ID映射")
                var idClearSuccessCount = 0
                DynamicIdManager.clearRegistry(SparkRegistries.TYPED_ANIMATION.key().location().toString())
                idClearSuccessCount++
                DynamicIdManager.clearRegistry(SparkRegistries.MODELS.key().location().toString())
                idClearSuccessCount++
                DynamicIdManager.clearRegistry(SparkRegistries.DYNAMIC_TEXTURES.key().location().toString())
                idClearSuccessCount++
//                DynamicIdManager.clearRegistry(SparkRegistries.JS_SCRIPTS.key().location().toString())
//                idClearSuccessCount++
                DynamicIdManager.clearRegistry(SparkRegistries.IK_COMPONENT_TYPE.key().location().toString())
                idClearSuccessCount++
                SparkCore.LOGGER.info("降级方案成功：清理了 {} 个注册表的ID映射", idClearSuccessCount)
            } catch (fallbackException: Exception) {
                SparkCore.LOGGER.error("降级方案也失败了", fallbackException)
            }
        }
        
        if (registryCount == 5) {
            SparkCore.LOGGER.info("成功清理 {} 个动态注册表", registryCount)
        }

        SparkCore.LOGGER.info("客户端登出清理完成：注册表 {}/5", registryCount)
    }
}
