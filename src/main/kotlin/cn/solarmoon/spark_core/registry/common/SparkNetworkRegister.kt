package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.network.dynamic.NetworkHandler
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Spark 网络注册器
 */
object SparkNetworkRegister {

    /**
     * 注册网络处理器
     *
     * @param bus 事件总线
     */
    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::registerPayloadHandlers)
    }

    /**
     * 注册有效载荷处理器
     *
     * @param event 注册事件
     */
    private fun registerPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        // 注册动态注册表同步处理器
        NetworkHandler.register(event)
    }
}
