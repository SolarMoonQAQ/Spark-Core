package cn.solarmoon.spark_core.web.dto

/**
 * 状态动画替换请求DTO
 * 字段命名与现有RPC参数保持一致
 * @param state 状态名称，对应RPC中的"state"参数
 * @param animation 动画名称，对应RPC中的"animation"参数
 * @param entityId 实体ID，对应RPC中的"entityId"参数，可为null表示使用当前玩家
 */
data class AnimationReplaceStateRequest(
    val state: String,
    val animation: String,
    val entityId: Int? = null
)
