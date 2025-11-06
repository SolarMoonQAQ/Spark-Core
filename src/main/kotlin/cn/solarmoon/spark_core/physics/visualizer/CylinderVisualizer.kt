package cn.solarmoon.spark_core.physics.visualizer

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.toVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
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

class CylinderVisualizer : ShapeVisualizer {

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
        if (shape is CylinderCollisionShape) {
            val halfExtents = shape.getHalfExtents(null).toVector3f()
            val axis = shape.axis // 0=X, 1=Y, 2=Z
            // 根据轴向确定半径和高度
            val radius = when (axis) {
                0 -> { // X轴方向
                    maxOf(halfExtents.y, halfExtents.z)
                }
                1 -> { // Y轴方向
                    maxOf(halfExtents.x, halfExtents.z)
                }
                2 -> { // Z轴方向
                    maxOf(halfExtents.x, halfExtents.y)
                }
                else -> maxOf(halfExtents.y, halfExtents.z)
            }
            val color = if (body.isColliding) Color.RED.rgb else Color.WHITE.rgb
            renderCylinderWireframe(transform, radius, shape.height, axis, color, camPos, poseStack, bufferSource)
        }
    }

    companion object {
        fun renderCylinderWireframe(
            transform: Matrix4f,
            radius: Float,
            height: Float,
            axis: Int,
            color: Int,
            camPos: Vec3,
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            segments: Int = 16
        ) {
            val buffer = bufferSource.getBuffer(RenderType.lines())
            val poseMatrix = poseStack.last().pose()
            // 顶部圆环
            val topOffset = when (axis) {
                0 -> Vector3f(height / 2f, 0f, 0f) // X轴
                1 -> Vector3f(0f, height / 2f, 0f) // Y轴
                2 -> Vector3f(0f, 0f, height / 2f) // Z轴
                else -> Vector3f(0f, height / 2f, 0f)
            }
            SphereVisualizer.renderCircle(transform, radius, axis, topOffset, segments, color, camPos, poseStack, buffer)

            // 底部圆环
            val bottomOffset = when (axis) {
                0 -> Vector3f(-height / 2f, 0f, 0f) // X轴
                1 -> Vector3f(0f, -height / 2f, 0f) // Y轴
                2 -> Vector3f(0f, 0f, -height / 2f) // Z轴
                else -> Vector3f(0f, -height / 2f, 0f)
            }
            SphereVisualizer.renderCircle(transform, radius, axis, bottomOffset, segments, color, camPos, poseStack, buffer)

            // 绘制几条竖线连接顶部和底部
            for (i in 0 until segments step segments / 4) {
                val angle = 2f * Math.PI.toFloat() * i / segments

                // 顶部点
                val topPoint = when (axis) {
                    0 -> Vector3f(height / 2f, radius * cos(angle), radius * sin(angle)) // X轴
                    1 -> Vector3f(radius * cos(angle), height / 2f, radius * sin(angle)) // Y轴
                    2 -> Vector3f(radius * cos(angle), radius * sin(angle), height / 2f) // Z轴
                    else -> Vector3f(radius * cos(angle), height / 2f, radius * sin(angle))
                }

                // 底部点
                val bottomPoint = when (axis) {
                    0 -> Vector3f(-height / 2f, radius * cos(angle), radius * sin(angle)) // X轴
                    1 -> Vector3f(radius * cos(angle), -height / 2f, radius * sin(angle)) // Y轴
                    2 -> Vector3f(radius * cos(angle), radius * sin(angle), -height / 2f) // Z轴
                    else -> Vector3f(radius * cos(angle), -height / 2f, radius * sin(angle))
                }

                val from = transform.transformPosition(topPoint, Vector3f()).sub(camPos.x.toFloat(), camPos.y.toFloat(), camPos.z.toFloat())
                val to = transform.transformPosition(bottomPoint, Vector3f()).sub(camPos.x.toFloat(), camPos.y.toFloat(), camPos.z.toFloat())
                val normal = to.sub(from, Vector3f()).normalize()

                buffer.addVertex(poseMatrix, from.x, from.y, from.z)
                    .setColor(color)
                    .setNormal(poseStack.last(), normal.x, normal.y, normal.z)
                buffer.addVertex(poseMatrix, to.x, to.y, to.z)
                    .setColor(color)
                    .setNormal(poseStack.last(), normal.x, normal.y, normal.z)
            }
        }
    }
}