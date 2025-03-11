package cn.solarmoon.spark_core.visual_effect.shape

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toMatrix4f
import cn.solarmoon.spark_core.physics.visualizer.ShapeVisualizerRegistry
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3

class ShapeRenderer: VisualEffectRenderer() {

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
        val physLevel:PhysicsLevel = level.physicsLevel
        physLevel.world.pcoList.forEach { body ->
            if (body.collideWithGroups == 0) return@forEach
            val shape = body.collisionShape
            var lerpPos: Vector3f
            if(body is PhysicsRigidBody && body.isDynamic)
                lerpPos = body.lastMcTickPos.mult(1-partialTicks).add(body.mcTickPos.mult(partialTicks))
            else if(body is PhysicsRigidBody && body.isStatic)
                lerpPos = body.getPhysicsLocation(null)
            else
                lerpPos = body.mcTickPos.mult(1-partialTicks).add(body.getPhysicsLocation(null).mult(partialTicks))
            if (shape is CompoundCollisionShape) {
                shape.listChildren().forEach {
                    val visualizer = ShapeVisualizerRegistry.getVisualizer(it.shape) ?: return@forEach
                    val parentTransform = body.getTransform(null).setTranslation(lerpPos).toTransformMatrix().toMatrix4f()
                    val childTransform = it.copyTransform(null).toTransformMatrix().toMatrix4f()
                    val finalMatrix = parentTransform.mul(childTransform)
                    visualizer.render(physLevel, body, finalMatrix, it.shape, mc, camPos, poseStack, bufferSource, partialTicks)
                }
            } else {
                val visualizer = ShapeVisualizerRegistry.getVisualizer(shape) ?: return@forEach
                visualizer.render(physLevel, body, body.getTransform(null).setTranslation(lerpPos).toTransformMatrix().toMatrix4f(), shape, mc, camPos, poseStack, bufferSource, partialTicks)
            }
        }
    }

}