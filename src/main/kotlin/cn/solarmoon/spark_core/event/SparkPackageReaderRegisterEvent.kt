package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

class SparkPackageReaderRegisterEvent(
    private val registry: MutableMap<String, SparkPackModule>
): Event(), IModBusEvent {

    fun register(reader: SparkPackModule) {
        registry[reader.id] = reader
    }

}