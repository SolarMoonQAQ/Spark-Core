package cn.solarmoon.spark_core.blueprint.data

/**
 * 占位数据类：蓝图变量定义
 * @property name 变量名
 * @property value 变量值
 */
data class BlueprintVariable(
    val name: String = "",
    val value: Any? = null
)
