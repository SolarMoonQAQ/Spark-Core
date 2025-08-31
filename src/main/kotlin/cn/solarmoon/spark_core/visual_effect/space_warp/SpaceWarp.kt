package cn.solarmoon.spark_core.visual_effect.space_warp

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

data class SpaceWarp(
    val pos: Vec3,
    val minRadius: Float,
    val maxRadius: Float,    // 最大扩散半径（米）
    val baseStrength: Float,       // 初始强度
    val maxLifeTime: Int,          // 存活 tick
) {
    var age = 0
        private set

    val alive get() = age < maxLifeTime

    fun tick() { age++ }

    /** 归一化进度 0~1 */
    fun getProgress(partialTicks: Float) = (age.toFloat() + partialTicks) / maxLifeTime.toFloat()

    /** 当前半径：前期加速，后期减速 */
    fun getRadius(partialTicks: Float): Float {
        val p = getProgress(partialTicks)
        return Mth.lerp(p, minRadius, maxRadius)
    }

    /** 当前强度：初始爆发高，逐渐衰减 */
    fun getStrength(partialTicks: Float): Float {
        val p = getProgress(partialTicks)
        var s = baseStrength * (1f - p * 0.85f)
        return s.coerceAtLeast(0f)
    }
}
