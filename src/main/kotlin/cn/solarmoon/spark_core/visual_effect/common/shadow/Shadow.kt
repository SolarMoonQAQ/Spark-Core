package cn.solarmoon.spark_core.visual_effect.common.shadow

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimData
import cn.solarmoon.spark_core.phys.toRadians
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
import kotlin.math.PI

class Shadow(
    val pos: Vec3,
    val yRot: Float,
    val animData: AnimData,
    val maxLifeTime: Int,
    val color: Color
) {
    constructor(animatable: IEntityAnimatable<*>, maxLifeTime: Int = 20, color: Color = Color.GRAY):
            this(animatable.animatable.position(), animatable.animatable.yRot.toRadians(), animatable.animData.copy(), maxLifeTime, color) {
                val entity = animatable.animatable
                if (entity is AbstractClientPlayer) textureLocation = entity.skin.texture
            }

    var tick = 0
    var isRemoved = false
    var textureLocation = animData.textureLocation

    val playData get() = animData.playData
    fun getProgress(partialTicks: Float = 0f): Float = ((tick.toFloat() + partialTicks) / maxLifeTime).coerceIn(0f, 1f)

    fun tick() {
        if (tick < maxLifeTime) tick++
        else isRemoved = true
    }

    fun render(level: Level, poseStack: PoseStack, bufferSource: MultiBufferSource, partialTicks: Float) {
        val buffer = bufferSource.getBuffer(RenderTypeUtil.transparentRepair(textureLocation))
        val posMa = Matrix4f().translate(pos.toVector3f()).rotateY(PI.toFloat() - yRot)
        val extMa = mapOf<String, Matrix4f>()
        val normal = poseStack.last().normal()
        val light = LevelRenderer.getLightColor(level, BlockPos(pos.toVec3i()).above())
        val overlay = OverlayTexture.NO_OVERLAY
        val color = ColorUtil.getColorAndSetAlpha(color.rgb, 1 - getProgress(partialTicks))
        animData.model.renderBones(playData, posMa, extMa, normal, buffer, light, overlay, color)
    }

}