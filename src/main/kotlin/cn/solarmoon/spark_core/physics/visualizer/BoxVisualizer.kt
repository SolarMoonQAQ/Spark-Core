package cn.solarmoon.spark_core.physics.visualizer

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.mesh.BoxShapeMesh
import cn.solarmoon.spark_core.physics.toMatrix4f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.math.Transform
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.Color

class BoxVisualizer: ShapeVisualizer {

    override fun render(
        level: PhysicsLevel,
        body: PhysicsCollisionObject,
        transform: Matrix4f,
        shape: CollisionShape,
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        if (shape is BoxCollisionShape) {
            val mesh = BoxShapeMesh().update(shape)
            poseStack.pushPose()
            poseStack.mulPose(transform)
            val matrix = poseStack.last().pose()
            val buffer = bufferSource.getBuffer(RenderType.lines())
            val vertices = mesh.vertices
            val edges = mesh.edgesOrder
            for (i in edges.indices step 2) {
                val from = vertices[edges[i]].sub(camPos.toVector3f(), Vector3f())
                val to = vertices[edges[i+1]].sub(camPos.toVector3f(), Vector3f())
                val normal = to.sub(from, Vector3f()).normalize()
                buffer.addVertex(matrix, from.x, from.y, from.z).setColor(Color.RED.rgb).setNormal(poseStack.last(), normal.x, normal.y, normal.z)
                buffer.addVertex(matrix, to.x, to.y, to.z).setColor(Color.RED.rgb).setNormal(poseStack.last(), normal.x, normal.y, normal.z)
            }
            poseStack.popPose()
        }
    }

}