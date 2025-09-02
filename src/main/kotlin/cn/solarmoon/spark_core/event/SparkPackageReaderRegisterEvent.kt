package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.resource2.SparkPackModule
import net.neoforged.bus.api.Event

class SparkPackageReaderRegisterEvent(
    private val registry: MutableMap<String, SparkPackModule>
): Event() {

    fun register(reader: SparkPackModule) {
        registry[reader.moduleName] = reader
    }

}