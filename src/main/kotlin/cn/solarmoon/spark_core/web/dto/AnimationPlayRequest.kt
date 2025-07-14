package cn.solarmoon.spark_core.web.dto

/**
 * 动画播放请求DTO
 * 字段命名与现有RPC参数保持一致
 * @param name 动画名称，对应RPC中的"name"参数
 * @param transTime 过渡时间（单位：tick），对应RPC中的"transTime"参数，默认为0
 * @param entityId 实体ID，对应RPC中的"entityId"参数，可为null表示使用当前玩家
 */
data class AnimationPlayRequest(
    val name: String,
    val transTime: Int = 0,
    val entityId: Int? = null
)
