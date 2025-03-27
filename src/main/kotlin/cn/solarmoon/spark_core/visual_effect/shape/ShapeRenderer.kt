package cn.solarmoon.spark_core.visual_effect.shape

import cn.solarmoon.spark_core.physics.lerp
import cn.solarmoon.spark_core.physics.level.ClientPhysicsLevel
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toMatrix4f
import cn.solarmoon.spark_core.physics.visualizer.ShapeVisualizerRegistry
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Matrix4f
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3

class ShapeRenderer : VisualEffectRenderer() {

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
        val physLevel = level.physicsLevel as ClientPhysicsLevel
        physLevel.world.pcoList.forEach { body ->
            if (body.collideWithGroups == 0) return@forEach
            val shape = body.collisionShape
            
            // 判断是否为地形静态刚体
            val isTerrainBody = (body is PhysicsRigidBody) && 
                                body.name.equals("terrain") && 
                                body.isStatic
            
            // 为地形刚体使用静态渲染（不进行插值），为动态刚体保持插值渲染
            val transform = if (isTerrainBody) {
                // 静态地形刚体直接使用当前变换，不进行插值
                body.getTransform(null).toTransformMatrix().toMatrix4f()
            } else {
                // 动态刚体使用插值渲染，确保平滑
                body.tickTransform.lerp(body.getTransform(null), partialTicks).toTransformMatrix().toMatrix4f()
            }
            
            if (shape is CompoundCollisionShape) {
                shape.listChildren().forEach {
                    val visualizer = ShapeVisualizerRegistry.getVisualizer(it.shape) ?: return@forEach
                    val parentTransform = org.joml.Matrix4f(transform)
                    val childTransform = it.copyTransform(null).toTransformMatrix().toMatrix4f()
                    val finalMatrix = parentTransform.mul(childTransform)
                    visualizer.render(
                        physLevel,
                        body,
                        finalMatrix,
                        it.shape,
                        mc,
                        camPos,
                        poseStack,
                        bufferSource,
                        partialTicks
                    )
                }
            } else {
                val visualizer = ShapeVisualizerRegistry.getVisualizer(shape) ?: return@forEach
                visualizer.render(physLevel, body, transform, shape, mc, camPos, poseStack, bufferSource, partialTicks)
            }
        }
    }

}