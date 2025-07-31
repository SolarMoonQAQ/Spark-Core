package cn.solarmoon.spark_core.visual_effect.trail

import net.minecraft.resources.ResourceLocation
import java.awt.Color

data class TrailInfo(
    val textureLocation: ResourceLocation,
    val lifeTime: Int = 15,
    val color: Color = Color.WHITE
) {
}