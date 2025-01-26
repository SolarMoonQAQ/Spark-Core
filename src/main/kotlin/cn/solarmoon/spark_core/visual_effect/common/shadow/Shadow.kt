package cn.solarmoon.spark_core.visual_effect.common.shadow

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.renderer.render
import cn.solarmoon.spark_core.phys.thread.ClientPhysLevel
import cn.solarmoon.spark_core.phys.thread.getPhysLevel
import cn.solarmoon.spark_core.util.ColorUtil
import cn.solarmoon.spark_core.util.RenderTypeUtil
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3i
import java.awt.Color

class Shadow(
    val animatable: IAnimatable<*>,
    val pos: Vec3,
    val yRotR: Float,
    val maxLifeTime: Int,
    val color: Color
) {
    constructor(animatable: IEntityAnimatable<*>, maxLifeTime: Int = 20, color: Color = Color.GRAY):
            this(animatable, animatable.animatable.position(), animatable.getRootYRot(), maxLifeTime, color) {
                val entity = animatable.animatable
                if (entity is AbstractClientPlayer) textureLocation = entity.skin.texture
            }

    var tick = 0
    var isRemoved = false
    var textureLocation = animatable.modelIndex.textureLocation
    val boneCache = animatable.bones.copy()

    fun getProgress(partialTicks: Float = 0f): Float = ((tick.toFloat() + partialTicks) / maxLifeTime).coerceIn(0f, 1f)

    fun tick() {
        if (tick < maxLifeTime) tick++
        else isRemoved = true
    }

    fun render(level: Level, poseStack: PoseStack, bufferSource: MultiBufferSource, partialTicks: Float) {
        val physPartialTicks = (level.getPhysLevel() as ClientPhysLevel).partialTicks.toFloat()
        val buffer = bufferSource.getBuffer(RenderTypeUtil.transparentRepair(textureLocation))
        val posMa = Matrix4f().translate(pos.toVector3f()).rotateY(yRotR)
        val normal = poseStack.last().normal()
        val light = LevelRenderer.getLightColor(level, BlockPos(pos.toVec3i()).above())
        val overlay = OverlayTexture.NO_OVERLAY
        val color = ColorUtil.getColorAndSetAlpha(color.rgb, 1 - getProgress(partialTicks))
        animatable.model.render(boneCache, posMa, normal, buffer, light, overlay, color, partialTicks, physPartialTicks)
    }

}