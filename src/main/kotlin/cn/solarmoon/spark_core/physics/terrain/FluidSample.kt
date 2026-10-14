package cn.solarmoon.spark_core.physics.terrain

/**
 * 单点流体采样结果。
 *
 * Spark-Core 只负责提供「是什么流体」与「液面在哪」，**不做**任何阈值/饱和/力的语义判断
 * （那是下游模组的事，见 `section_fluid_index_plan.md` §5.5）。
 *
 * @property fluidId 全局 fluid id（`BuiltInRegistries.FLUID`），供下游查 `FluidPhysicsData`
 * @property surfaceY 该点所在流体柱的液面**世界 Y**
 * @property depth 浸没深度 = `surfaceY - y`，恒 > 0
 */
data class FluidSample(
    val fluidId: Int,
    val surfaceY: Float,
    val depth: Float
)
