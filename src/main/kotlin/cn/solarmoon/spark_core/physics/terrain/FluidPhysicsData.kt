package cn.solarmoon.spark_core.physics.terrain

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * 流体的物理属性（数据驱动，注册到 `Registries.FLUID` 的 DataMap）。
 *
 * 与 `BlockPhysicsData` 同构：后者是**接触力学**语义（摩擦/弹性），
 * 本类是**浮力/阻力**语义，二者服务于完全不同的物理通道。
 *
 * @property density 密度，kg/m³。默认 1000（水）
 * @property viscosity 动力粘度，Pa·s。默认 1.0e-3（水）
 * @property dragCoefficient 形状阻力系数。默认 0.47（球体）
 * @property buoyancyEnabled 是否产生浮力。熔岩可设为 false
 * @property surfaceBias 液面的额外偏置（格）。**默认 0 = 忠实原版**（自由液面在 voxel 底 +8/9，
 *           1/16 量化后为 0.875）。下游若想要「格子对齐」的液面，通过数据包配置此值，
 *           Spark-Core 不内置偏差。
 */
data class FluidPhysicsData(
    val density: Float,
    val viscosity: Float,
    val dragCoefficient: Float,
    val buoyancyEnabled: Boolean,
    val surfaceBias: Float
) {
    companion object {
        val DEFAULT: FluidPhysicsData = FluidPhysicsData(
            density = 1000f,
            viscosity = 1.0e-3f,
            dragCoefficient = 0.47f,
            buoyancyEnabled = true,
            surfaceBias = 0f
        )

        val CODEC: Codec<FluidPhysicsData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.optionalFieldOf("density", 1000f).forGetter(FluidPhysicsData::density),
                Codec.FLOAT.optionalFieldOf("viscosity", 1.0e-3f).forGetter(FluidPhysicsData::viscosity),
                Codec.FLOAT.optionalFieldOf("drag_coefficient", 0.47f).forGetter(FluidPhysicsData::dragCoefficient),
                Codec.BOOL.optionalFieldOf("buoyancy_enabled", true).forGetter(FluidPhysicsData::buoyancyEnabled),
                Codec.FLOAT.optionalFieldOf("surface_bias", 0f).forGetter(FluidPhysicsData::surfaceBias)
            ).apply(instance, ::FluidPhysicsData)
        }
    }
}
