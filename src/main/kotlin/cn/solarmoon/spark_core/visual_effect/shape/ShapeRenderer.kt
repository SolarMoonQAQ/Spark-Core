package cn.solarmoon.spark_core.visual_effect.shape

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toMatrix4f
import cn.solarmoon.spark_core.physics.visualizer.ShapeVisualizer
import cn.solarmoon.spark_core.physics.visualizer.ShapeVisualizerRegistry
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.math.Transform
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import java.awt.Color

class ShapeRenderer: VisualEffectRenderer() {

    private val visualizers = mutableMapOf<Long, ShapeVisualizer>()

    override fun tick() {

    }

    override fun physTick(physLevel: PhysicsLevel) {
    }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        val level = mc.level ?: return
        if (!mc.entityRenderDispatcher.shouldRenderHitBoxes()) return
        val physLevel = level.physicsLevel
        physLevel.world.pcoList.forEach { body ->
            val shape = body.collisionShape
            if (shape is CompoundCollisionShape) {
                shape.listChildren().forEach {
                    val visualizer = ShapeVisualizerRegistry.getVisualizer(it.shape) ?: return@forEach
                    val parentTransform = body.getTransform(null).toTransformMatrix().toMatrix4f()
                    val childTransform = it.copyTransform(null).toTransformMatrix().toMatrix4f()
                    val finalMatrix = parentTransform.mul(childTransform)
                    visualizer.render(physLevel, body, finalMatrix, it.shape, mc, camPos, poseStack, bufferSource, partialTicks)
                }
            } else {
                val visualizer = ShapeVisualizerRegistry.getVisualizer(shape) ?: return@forEach
                visualizer.render(physLevel, body, body.getTransform(null).toTransformMatrix().toMatrix4f(), shape, mc, camPos, poseStack, bufferSource, partialTicks)
            }
        }
    }

}