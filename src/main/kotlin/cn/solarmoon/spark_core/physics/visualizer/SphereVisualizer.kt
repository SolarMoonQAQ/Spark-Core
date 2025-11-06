package cn.solarmoon.spark_core.physics.visualizer

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

class SphereVisualizer : ShapeVisualizer {

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
        if (shape is SphereCollisionShape) {
            val radius = shape.radius
            val color = if (body.isColliding) Color.RED.rgb else Color.WHITE.rgb
            renderSphereWireframe(transform, radius, color, camPos, poseStack, bufferSource)
        }
    }

    companion object {
        fun renderSphereWireframe(
            transform: Matrix4f,
            radius: Float,
            color: Int,
            camPos: Vec3,
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            segments: Int = 16
        ) {
            val buffer = bufferSource.getBuffer(RenderType.lines())

            // 绘制三个垂直的圆环表示球体
            renderCircle(transform, radius, 2, Vector3f(), segments, color, camPos, poseStack, buffer) // XY平面
            renderCircle(transform, radius, 1, Vector3f(), segments, color, camPos, poseStack, buffer) // XZ平面
            renderCircle(transform, radius, 0, Vector3f(), segments, color, camPos, poseStack, buffer) // YZ平面
        }

        fun renderCircle(
            transform: Matrix4f,
            radius: Float,
            normalAxis: Int,
            centerOffset: Vector3f,
            segments: Int,
            color: Int,
            camPos: Vec3,
            poseStack: PoseStack,
            buffer: com.mojang.blaze3d.vertex.VertexConsumer
        ) {
            val poseMatrix = poseStack.last().pose()

            for (i in 0 until segments) {
                val angle1 = 2f * Math.PI.toFloat() * i / segments
                val angle2 = 2f * Math.PI.toFloat() * (i + 1) / segments

                val point1 = createCirclePoint(radius, angle1, normalAxis, centerOffset)
                val point2 = createCirclePoint(radius, angle2, normalAxis, centerOffset)

                val from = transform.transformPosition(point1, Vector3f()).sub(camPos.x.toFloat(), camPos.y.toFloat(), camPos.z.toFloat())
                val to = transform.transformPosition(point2, Vector3f()).sub(camPos.x.toFloat(), camPos.y.toFloat(), camPos.z.toFloat())
                val normal = to.sub(from, Vector3f()).normalize()
                buffer.addVertex(poseMatrix, from.x, from.y, from.z)
                    .setColor(color)
                    .setNormal(poseStack.last(), normal.x, normal.y, normal.z)
                buffer.addVertex(poseMatrix, to.x, to.y, to.z)
                    .setColor(color)
                    .setNormal(poseStack.last(), normal.x, normal.y, normal.z)
            }
        }

        private fun createCirclePoint(radius: Float, angle: Float, normalAxis: Int, centerOffset: Vector3f): Vector3f {
            val cos = cos(angle) * radius
            val sin = sin(angle) * radius

            return when (normalAxis) {
                0 -> Vector3f(centerOffset.x, centerOffset.y + cos, centerOffset.z + sin)  // YZ平面
                1 -> Vector3f(centerOffset.x + cos, centerOffset.y, centerOffset.z + sin)  // XZ平面
                else -> Vector3f(centerOffset.x + cos, centerOffset.y + sin, centerOffset.z)  // XY平面
            }
        }
    }
}