package cn.solarmoon.spark_core.physics.visualizer

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.mesh.BoxShapeMesh
import cn.solarmoon.spark_core.physics.component.component
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.Color

class BoxVisualizer: ShapeVisualizer {

    val mesh = BoxShapeMesh()

    override fun render(
        transform: Matrix4f,
        shape: CollisionShape,
        color: Color,
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        if (shape is BoxCollisionShape) {
            val mesh = mesh.update(shape)
            val buffer = bufferSource.getBuffer(RenderType.lines())
            val edges = mesh.edgesOrder
            for (i in edges.indices step 2) {
                val from = mesh.getWorldVertexPosition(edges[i], transform).sub(camPos.toVector3f(), Vector3f())
                val to = mesh.getWorldVertexPosition(edges[i+1], transform).sub(camPos.toVector3f(), Vector3f())
                val normal = to.sub(from, Vector3f()).normalize()
                buffer.addVertex(poseStack.last().pose(), from.x, from.y, from.z).setColor(color.rgb).setNormal(poseStack.last(), normal.x, normal.y, normal.z)
                buffer.addVertex(poseStack.last().pose(), to.x, to.y, to.z).setColor(color.rgb).setNormal(poseStack.last(), normal.x, normal.y, normal.z)
            }
        }
    }

}