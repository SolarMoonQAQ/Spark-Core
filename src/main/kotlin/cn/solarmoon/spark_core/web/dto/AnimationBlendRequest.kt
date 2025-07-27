package cn.solarmoon.spark_core.web.dto

/**
 * 动画混合请求DTO
 * 字段命名与现有RPC参数保持一致
 * @param anim1 第一个动画名称，对应RPC中的"anim1"参数
 * @param anim2 第二个动画名称，对应RPC中的"anim2"参数
 * @param weight 混合权重，对应RPC中的"weight"参数，默认为0.5
 * @param entityId 实体ID，对应RPC中的"entityId"参数，可为null表示使用当前玩家
 */
data class AnimationBlendRequest(
    val anim1: String,
    val anim2: String,
    val weight: Double = 0.5,
    val entityId: Int? = null
)
