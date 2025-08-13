package cn.solarmoon.spark_core.visual_effect.space_warp

import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.sin

data class SpaceWarp(
    val pos: Vec3,
    val maxRadiusMeters: Float,    // 最大扩散半径（米）
    val baseStrength: Float,       // 初始强度
    val maxLifeTime: Int,          // 存活 tick
    val pulseHz: Float = 0f        // 可选脉动频率
) {
    var age = 0
        private set

    val alive get() = age < maxLifeTime

    fun tick() { age++ }

    /** 归一化进度 0~1 */
    private val progress get() = age.toFloat() / maxLifeTime.toFloat()

    /** 当前半径：前期加速，后期减速 */
    fun radiusNow(): Float {
        val p = progress
        // 二次缓出曲线（ease-out quadratic）
        return maxRadiusMeters * (1f - (1f - p) * (1f - p))
    }

    /** 当前波带宽度：开头厚，中间适中，尾段再厚一点 */
    fun bandWidthNow(): Float {
        val p = progress
        return 2.5f - 0.5f * sin(p * PI).toFloat()
    }

    /** 当前强度：初始爆发高，逐渐衰减 */
    fun strengthNow(): Float {
        val p = progress
        var s = baseStrength * (1f - p * 0.85f)

        if (pulseHz > 0f) {
            val t = age / 20f
            s *= (0.5f + 0.5f * sin(2f * PI.toFloat() * pulseHz * t))
        }
        return s.coerceAtLeast(0f)
    }
}
