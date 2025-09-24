package cn.solarmoon.spark_core.particle

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.renderer.render
import cn.solarmoon.spark_core.registry.common.SparkParticles
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import cn.solarmoon.spark_core.util.SerializeHelper
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import org.joml.Matrix3f
import org.joml.Matrix4f
import java.awt.Color

class AnimatableShadowParticle(
    val animatable: IAnimatable<*>,
    val color: Color,
    lifeTime: Int,
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double
): Particle(level, x, y, z) {

    val yRot = animatable.getRootYRot()
    val boneCache = animatable.modelController.model?.pose?.copy()

    fun getProgress(partialTicks: Float = 1f): Float {
        return ((age + partialTicks) / lifetime).coerceIn(0f, 1f)
    }

    init {
        val colors = color.getColorComponents(null)
        setColor(colors[0], colors[1], colors[2])
        setLifetime(lifeTime)
    }

    override fun render(
        buffer: VertexConsumer,
        camera: Camera,
        partialTicks: Float
    ) {
        val posMa = Matrix4f().translate(pos.toVector3f()).rotateY(yRot)
        val light = getLightColor(partialTicks)
        val overlay = OverlayTexture.NO_OVERLAY
        setAlpha(1 - getProgress(partialTicks))
        val color = Color(rCol, gCol, bCol, alpha).rgb
        if (boneCache == null) return
        animatable.modelController.originModel.render(boneCache, posMa, Matrix3f(), buffer, light, overlay, color, partialTicks)
    }

    override fun getRenderType(): ParticleRenderType {
        return SparkParticleRenderType.entityLike(animatable.modelController.textureLocation)
    }

    class Option private constructor(
        val syncerType: SyncerType,
        val syncData: SyncData,
        val color: Color,
        val lifeTime: Int,
        private val type: ParticleType<Option>
    ): ParticleOptions {
        constructor(animatable: IAnimatable<*>, color: Color, lifeTime: Int): this(animatable.syncerType, animatable.syncData, color, lifeTime, SparkParticles.ANIMATABLE_SHADOW.get())
        override fun getType(): ParticleType<*> {
            return type
        }

        companion object {
            fun codec(type: ParticleType<Option>) = RecordCodecBuilder.mapCodec {
                it.group(
                    SyncerType.CODEC.fieldOf("syncer_type").forGetter(Option::syncerType),
                    SyncData.CODEC.fieldOf("sync_data").forGetter(Option::syncData),
                    SerializeHelper.COLOR_CODEC.fieldOf("color").forGetter(Option::color),
                    Codec.INT.fieldOf("life_time").forGetter(Option::lifeTime),
                ).apply(it) { t, d, c, l -> Option(t, d, c, l, type) }
            }

            fun streamCodec(type: ParticleType<Option>) = StreamCodec.composite(
                SyncerType.STREAM_CODEC, Option::syncerType,
                SyncData.STREAM_CODEC, Option::syncData,
                SerializeHelper.COLOR_STREAM_CODEC, Option::color,
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
            val animatable = type.syncerType.getSyncer(level, type.syncData) as IAnimatable<*>
            return AnimatableShadowParticle(animatable, type.color, type.lifeTime, level, x, y, z)
        }
    }

}