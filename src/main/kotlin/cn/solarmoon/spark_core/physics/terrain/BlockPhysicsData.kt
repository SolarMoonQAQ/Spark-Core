package cn.solarmoon.spark_core.physics.terrain

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class BlockPhysicsData(
    val friction: Float, // 摩擦系数
    val rollingFriction: Float, // 滚动摩擦系数
    val restitution: Float, // 弹性系数
    val baseSlip: Float, // 基础滑动系数
    val slipFactor: Float // 湿滑影响系数，最终滑动系数为baseSlip + humidity (0~1) * slipFactor
) {
    companion object {
        val CODEC: Codec<BlockPhysicsData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.optionalFieldOf("friction", 0.7f).forGetter(BlockPhysicsData::friction),
                Codec.FLOAT.optionalFieldOf("rolling_friction", 1f).forGetter(BlockPhysicsData::rollingFriction),
                Codec.FLOAT.optionalFieldOf("restitution", 0.3f).forGetter(BlockPhysicsData::restitution),
                Codec.FLOAT.optionalFieldOf("base_slip", 0f).forGetter(BlockPhysicsData::baseSlip),
                Codec.FLOAT.optionalFieldOf("slip_factor", 1f).forGetter(BlockPhysicsData::slipFactor)
            ).apply(instance, ::BlockPhysicsData)
        }
    }
}
