package cn.solarmoon.spark_core.blueprint.data

import java.util.*

/**
 * BlueprintGraph 数据类
 * @property id 唯一标识
 * @property name 图名称
 * @property nodes 节点列表
 * @property connections 连接列表
 * @property variables 变量列表
 * @property metadata 附加元数据
 */
data class BlueprintGraph(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val nodes: MutableList<BlueprintNode> = mutableListOf(),
    val connections: MutableList<BlueprintConnection> = mutableListOf(),
    val variables: MutableList<BlueprintVariable> = mutableListOf(),
    val metadata: Map<String, Any?> = emptyMap()
)
