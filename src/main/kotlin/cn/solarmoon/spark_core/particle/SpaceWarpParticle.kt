package cn.solarmoon.spark_core.particle

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.registry.common.SparkParticles
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import cn.solarmoon.spark_core.util.SerializeHelper
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.Mth
import java.awt.Color

class SpaceWarpParticle(
    val minRadius: Float,
    val maxRadius: Float,    // 最大扩散半径
    val baseStrength: Float,       // 初始强度
    lifeTime: Int,
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double
): Particle(level, x, y, z) {

    private var partialTicks = 0f
    
    init {
        setLifetime(lifeTime)
    }

    override fun render(
        buffer: VertexConsumer,
        camera: Camera,
        partialTicks: Float
    ) {
        this.partialTicks = partialTicks
        val rPos = pos.subtract(camera.position).toVector3f()
        val cx = rPos.x
        val cy = rPos.y
        val cz = rPos.z
        val r = getRadius(partialTicks)

        // 六个面，每面四个顶点
        // 前面 (z = -r)
        buffer.addVertex(cx - r, cy - r, cz - r)
        buffer.addVertex(cx + r, cy - r, cz - r)
        buffer.addVertex(cx + r, cy + r, cz - r)
        buffer.addVertex(cx - r, cy + r, cz - r)

        // 后面 (z = +r)
        buffer.addVertex(cx + r, cy - r, cz + r)
        buffer.addVertex(cx - r, cy - r, cz + r)
        buffer.addVertex(cx - r, cy + r, cz + r)
        buffer.addVertex(cx + r, cy + r, cz + r)

        // 左面 (x = -r)
        buffer.addVertex(cx - r, cy - r, cz + r)
        buffer.addVertex(cx - r, cy - r, cz - r)
        buffer.addVertex(cx - r, cy + r, cz - r)
        buffer.addVertex(cx - r, cy + r, cz + r)

        // 右面 (x = +r)
        buffer.addVertex(cx + r, cy - r, cz - r)
        buffer.addVertex(cx + r, cy - r, cz + r)
        buffer.addVertex(cx + r, cy + r, cz + r)
        buffer.addVertex(cx + r, cy + r, cz - r)

        // 上面 (y = +r)
        buffer.addVertex(cx - r, cy + r, cz - r)
        buffer.addVertex(cx + r, cy + r, cz - r)
        buffer.addVertex(cx + r, cy + r, cz + r)
        buffer.addVertex(cx - r, cy + r, cz + r)

        // 下面 (y = -r)
        buffer.addVertex(cx - r, cy - r, cz + r)
        buffer.addVertex(cx + r, cy - r, cz + r)
        buffer.addVertex(cx + r, cy - r, cz - r)
        buffer.addVertex(cx - r, cy - r, cz - r)
    }

    override fun getRenderType(): ParticleRenderType {
        return SparkParticleRenderType.distort(getProgress(partialTicks), getStrength(partialTicks))
    }

    fun getRadius(partialTicks: Float): Float {
        val p = getProgress(partialTicks)
        return Mth.lerp(p, minRadius, maxRadius)
    }

    fun getStrength(partialTicks: Float): Float {
        val p = getProgress(partialTicks)
        var s = baseStrength * (1f - p * 0.85f)
        return s.coerceAtLeast(0f)
    }

    fun getProgress(partialTicks: Float = 1f): Float {
        return ((age + partialTicks) / lifetime).coerceIn(0f, 1f)
    }

    class Option private constructor(
        val minRadius: Float,
        val maxRadius: Float,
        val baseStrength: Float,
        val lifeTime: Int,
        private val type: ParticleType<Option>
    ): ParticleOptions {
        constructor(minRadius: Float, maxRadius: Float, baseStrength: Float, lifeTime: Int): this(minRadius, maxRadius, baseStrength, lifeTime, SparkParticles.SPACE_WARP.get())

        override fun getType(): ParticleType<*> {
            return type
        }

        companion object {
            fun codec(type: ParticleType<Option>) = RecordCodecBuilder.mapCodec {
                it.group(
                    Codec.FLOAT.fieldOf("min_radius").forGetter(Option::minRadius),
                    Codec.FLOAT.fieldOf("max_radius").forGetter(Option::maxRadius),
                    Codec.FLOAT.fieldOf("strength").forGetter(Option::baseStrength),
                    Codec.INT.fieldOf("life_time").forGetter(Option::lifeTime),
                ).apply(it) { t, d, c, l -> Option(t, d, c, l, type) }
            }

            fun streamCodec(type: ParticleType<Option>) = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Option::minRadius,
                ByteBufCodecs.FLOAT, Option::maxRadius,
                ByteBufCodecs.FLOAT, Option::baseStrength,
                ByteBufCodecs.INT, Option::lifeTime
            ) { syncerType, syncData, c, l -> Option(syncerType, syncData, c, l, type) }
        }
    }

    class Provider: ParticleProvider<Option> {
        override fun createParticle(
            type: Option,
            level: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            xSpeed: Double,
            ySpeed: Double,
            zSpeed: Double
        ): Particle {
            return SpaceWarpParticle(type.minRadius, type.maxRadius, type.baseStrength, type.lifeTime, level, x, y, z)
        }
    }

}