package cn.solarmoon.spark_core.blueprint.data

/**
 * 占位数据类：蓝图连接定义
 * @property from 起始节点ID
 * @property to 目标节点ID
 */
data class BlueprintConnection(
    val id: String,
    val fromNodeId: String,
    val fromPinId: String,
    val toNodeId: String,
    val toPinId: String,
    val connectionType: PinType
)
