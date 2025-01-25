package cn.solarmoon.spark_core.util

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.state.BlockState

/**
 * 像三叉戟一样，物品栏显示2d，手持3d
 */
class PerspectiveBakedModel(private val bakedModel2d: BakedModel, private val bakedModel3d: BakedModel): BakedModel {

    override fun getQuads(state: BlockState?, side: Direction?, rand: RandomSource): List<BakedQuad> {
        return emptyList()
    }

    override fun usesBlockLight(): Boolean {
        return bakedModel2d.usesBlockLight()
    }

    override fun getParticleIcon(): TextureAtlasSprite {
        return bakedModel2d.particleIcon
    }

    override fun useAmbientOcclusion(): Boolean {
        return bakedModel2d.useAmbientOcclusion()
    }

    override fun isGui3d(): Boolean {
        return bakedModel2d.isGui3d
    }

    override fun isCustomRenderer(): Boolean {
        return false
    }

    override fun getOverrides(): ItemOverrides {
        return ItemOverrides.EMPTY
    }

    override fun applyTransform(type: ItemDisplayContext, mat: PoseStack, applyLeftHandTransform: Boolean): BakedModel {
        return if (type == ItemDisplayContext.GUI || type == ItemDisplayContext.FIXED || type == ItemDisplayContext.GROUND) {
            bakedModel2d.applyTransform(type, mat, applyLeftHandTransform)
        } else {
            bakedModel3d.applyTransform(type, mat, applyLeftHandTransform)
        }
    }

}