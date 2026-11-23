package cn.solarmoon.spark_core.animation

import net.minecraft.resources.ResourceLocation

/**
 * 动画资源键：资源类型 + 位置。
 *
 * 动画集与控制器集各自在独立的表里按本键查找。类型取自内容包目录的第一段
 * （{@code part}、{@code entity} 等），因此类型不同、位置相同的两份资源不会串到一起。
 *
 * 键不包含 [cn.solarmoon.spark_core.animation.model.ModelIndex.fullDetailDistance]：
 * LOD 只决定渲染细节，不是资源身份的一部分。
 *
 * @param type 资源类型，对应内容包目录第一段
 * @param location 资源位置
 */
data class AnimResourceKey(val type: String, val location: ResourceLocation)
