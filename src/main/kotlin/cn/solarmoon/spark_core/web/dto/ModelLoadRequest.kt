package cn.solarmoon.spark_core.web.dto

/**
 * 模型加载请求DTO
 * 字段命名与现有RPC参数保持一致
 * @param path 模型路径，对应RPC中的"path"参数，格式为"namespace:path"
 * @param entityId 实体ID，对应RPC中的"entityId"参数，可为null表示使用当前玩家
 */
data class ModelLoadRequest(
    val path: String,
    val entityId: Int? = null
)
