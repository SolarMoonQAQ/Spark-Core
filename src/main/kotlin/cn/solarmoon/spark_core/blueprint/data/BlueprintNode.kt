package cn.solarmoon.spark_core.blueprint.data

import cn.solarmoon.spark_core.blueprint.data.Point2D

/**
 * 占位数据类：蓝图节点定义
 * @property id 节点唯一标识
 * @property type 节点类型标识
 */
data class BlueprintNode(
    val id: String = "",
    val type: String = "",
    val position: Point2D = Point2D(),
    val inputs: MutableList<Pin> = mutableListOf(),
    val outputs: MutableList<Pin> = mutableListOf(),
    val properties: Map<String, Any> = emptyMap()
)
