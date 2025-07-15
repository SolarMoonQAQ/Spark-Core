package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent
import org.jetbrains.annotations.NotNull

/**
 * <p>事件 - 注册 Molang 绑定</p>
 * <p>Event - Register Molang Binding</p>
 * <p>用于注册特定前缀的molang变量或函数，如custom.foo</p>
 * <p>Used to register molang variables or functions with a specific prefix, such as custom.foo</p>
 * <p>触发于ModEventBus</p>
 * <p>Fired on ModEventBus</p>
 */
class MolangBindingRegisterEvent(
    private val extraBindings: Map<String?, ObjectBinding?>? = HashMap()
): Event(), IModBusEvent {
    @NotNull
    fun getBindings(): Map<String?, ObjectBinding?>? {
        return extraBindings
    }
}