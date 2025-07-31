package cn.solarmoon.spark_core.visual_effect.trail

import cn.solarmoon.spark_core.util.catmullromVector3f
import cn.solarmoon.spark_core.util.lerp
import cn.solarmoon.spark_core.util.setAlpha
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import java.awt.Color

data class TrailPoint(
    val start: Vector3f,
    val end: Vector3f,
    val info: TrailInfo
) {

    var lifeTime = info.lifeTime
        private set

    val center get() = start.lerp(end, 0.5f, Vector3f())

    val length get() = start.distance(end)

    val isExpired get() = lifeTime <= 0

    fun tick() {
        if (lifeTime > 0) lifeTime--
    }

    fun getProgress(partialTicks: Float): Float {
        return (1 - (lifeTime - partialTicks) / info.lifeTime.toFloat()).coerceIn(0f, 1f)
    }

    fun getColor(partialTicks: Float): Color {
        return info.color.setAlpha(1 - getProgress(partialTicks))
    }

    companion object {
        /**
         * 使用Catmull-Rom样条曲线在多个TrailPoint之间进行插值
         * 生成平滑的轨迹曲线
         */
        fun catmullRomSpline(
            prev: TrailPoint,
            now: TrailPoint,
            next: TrailPoint,
            t: Float
        ): TrailPoint {
            // 核心：通过now到next的趋势推断nextNext点（避免依赖未来点）
            // 1. 计算now到next的变化量（向量差）
            val deltaStart = next.start - now.start  // 起点变化趋势
            val deltaEnd = next.end - now.end        // 终点变化趋势

            // 2. 外推nextNext点：假设变化趋势延续（线性外推）
            val nextNextStartInferred = next.start + deltaStart  // next的起点 + 同样的变化量
            val nextNextEndInferred = next.end + deltaEnd        // next的终点 + 同样的变化量

            // 3. 使用推断的nextNext计算插值
            val interpolatedStart = catmullromVector3f(
                prev.start,
                now.start,
                next.start,
                nextNextStartInferred,  // 用推断值代替实际nextNext
                t
            )

            val interpolatedEnd = catmullromVector3f(
                prev.end,
                now.end,
                next.end,
                nextNextEndInferred,    // 用推断值代替实际nextNext
                t
            )

            return TrailPoint(
                start = interpolatedStart,
                end = interpolatedEnd,
                info = now.info  // 保留当前点的信息
            )
        }

        operator fun Vector3f.minus(other: Vector3f): Vector3f {
            return Vector3f(
                x - other.x,
                y - other.y,
                z - other.z
            )
        }

        operator fun Vector3f.plus(other: Vector3f): Vector3f {
            return Vector3f(
                x + other.x,
                y + other.y,
                z + other.z
            )
        }


        fun bezierQuadratic(p0: TrailPoint, p1: TrailPoint, p2: TrailPoint, t: Float): TrailPoint {
            val start = Vector3f()
            val end = Vector3f()

            // 二次贝塞尔插值起点和终点
            val u = 1 - t
            val tt = t * t
            val uu = u * u

            start.set(p0.start).mul(uu)
                .add(p1.start.mul(2 * u * t, Vector3f()))
                .add(p2.start.mul(tt, Vector3f()))

            end.set(p0.end).mul(uu)
                .add(p1.end.mul(2 * u * t, Vector3f()))
                .add(p2.end.mul(tt, Vector3f()))

            return TrailPoint(
                start = start,
                end = end,
                info = p2.info
            )
        }

        fun bezierCubic(p0: TrailPoint, p1: TrailPoint, p2: TrailPoint, p3: TrailPoint, t: Float): TrailPoint {
            val start = Vector3f()
            val end = Vector3f()

            // 三次贝塞尔插值起点和终点
            val u = 1 - t
            val tt = t * t
            val uu = u * u
            val uuu = uu * u
            val ttt = tt * t

            start.set(p0.start).mul(uuu)
                .add(p1.start.mul(3 * uu * t, Vector3f()))
                .add(p2.start.mul(3 * u * tt, Vector3f()))
                .add(p3.start.mul(ttt, Vector3f()))

            end.set(p0.end).mul(uuu)
                .add(p1.end.mul(3 * uu * t, Vector3f()))
                .add(p2.end.mul(3 * u * tt, Vector3f()))
                .add(p3.end.mul(ttt, Vector3f()))

            return TrailPoint(
                start = start,
                end = end,
                info = p3.info
            )
        }
    }
}