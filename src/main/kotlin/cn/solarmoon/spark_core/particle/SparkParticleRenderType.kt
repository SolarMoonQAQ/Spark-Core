package cn.solarmoon.spark_core.particle

import cn.solarmoon.spark_core.registry.client.SparkShaders
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.ResourceLocation

object SparkParticleRenderType {

    fun entityLike(texture: ResourceLocation) = object : ParticleRenderType {
        override fun begin(
            tesselator: Tesselator,
            textureManager: TextureManager
        ): BufferBuilder {
            RenderSystem.depthMask(true)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderType.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER.setupRenderState()
            RenderStateShard.TextureStateShard(texture, false, true).setupRenderState()
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY)
        }

        override fun toString(): String {
            return "ENTITY_LIKE"
        }
    }

    fun distort(time: Float, strength: Float) = object : ParticleRenderType {
        override fun begin(
            tesselator: Tesselator,
            textureManager: TextureManager
        ): BufferBuilder {
            RenderSystem.depthMask(false)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShader { SparkShaders.DISTORT_SHADER.apply {
                RenderSystem.setShaderTexture(0, Minecraft.getInstance().mainRenderTarget.colorTextureId)
                safeGetUniform("Time").set(time)
                safeGetUniform("Strength").set(strength)
            } }
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        }

        override fun toString(): String {
            return "DISTORT"
        }
    }

}