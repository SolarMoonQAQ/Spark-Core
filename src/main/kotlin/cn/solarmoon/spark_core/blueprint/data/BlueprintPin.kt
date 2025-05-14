package cn.solarmoon.spark_core.blueprint.data

data class Pin(
    val id: String,
    val name: String,
    val pinType: PinType,
    val direction: PinDirection? = null,
    val dataType: PinDataType? = null,
    val defaultValue: Any? = null,
    var isConnected: Boolean = false,
    val nodeId: String
)
