package cn.solarmoon.spark_core.physics.visualizer

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.copy
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

class CapsuleVisualizer : ShapeVisualizer {

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
        if (shape is CapsuleCollisionShape) {
            val radius = shape.radius
            val height = shape.height
            val axis = shape.axis // 0=X, 1=Y, 2=Z
            val color = if (body.isColliding) Color.RED.rgb else Color.WHITE.rgb
            renderCapsuleWireframe(transform, radius, height, axis, color, camPos, poseStack, bufferSource)
        }
    }

    companion object {
        fun renderCapsuleWireframe(
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

            // 绘制圆柱体部分
            if (height > 0) {
                CylinderVisualizer.renderCylinderWireframe(
                    transform,
                    radius,
                    height,
                    axis,
                    color,
                    camPos,
                    poseStack,
                    bufferSource
                )
            }
            // 绘制上半球
            var offset = Vector3f()
            when (axis) {
                0 -> offset.set(height / 2f, 0f, 0f) // X轴
                1 -> offset.set(0f, height / 2f, 0f) // Y轴
                else -> offset.set(0f, 0f, height / 2f) // Z轴
            }
            renderHemisphere(transform, radius, axis, offset, segments, color, camPos, poseStack, buffer, true)
            // 绘制下半球
            offset.negate()
            renderHemisphere(transform, radius, axis, offset, segments, color, camPos, poseStack, buffer, false)
        }

        private fun renderHemisphere(
            transform: Matrix4f,
            radius: Float,
            axis: Int,
            offset: Vector3f,
            segments: Int,
            color: Int,
            camPos: Vec3,
            poseStack: PoseStack,
            buffer: VertexConsumer,
            isTop: Boolean
        ) {
            val direction = if (isTop) 1f else -1f
            // 用三个不同半径的圆环表示半球
            val x = if (axis == 0) 1f else 0f
            val y = if (axis == 1) 1f else 0f
            val z = if (axis == 2) 1f else 0f
            for (i in setOf(5, 9)) {
                val currentRadius = radius * (( i)/ 10f)
                val yOffset = (direction * radius * sin(Math.acos(((i )/ 10f).toDouble())).toFloat())
                val offset1 = Vector3f(x, y, z).mul(yOffset).add(offset)
                // 绘制与胶囊体轴向垂直的圆环
                SphereVisualizer.renderCircle(transform, currentRadius, axis, offset1, segments, color, camPos, poseStack, buffer)
            }
            // 绘制与胶囊体轴向平行的圆环（半球的两侧）
            for (sideAxis in getSideAxes(axis)) {
                SphereVisualizer.renderCircle(transform, radius, sideAxis,
                    offset, segments, color, camPos, poseStack, buffer)
            }
        }

        private fun getSideAxes(normalAxis: Int): List<Int> {
            return when (normalAxis) {
                0 -> listOf(1, 2) // X轴的法线，侧面用Y和Z轴圆环
                1 -> listOf(0, 2) // Y轴的法线，侧面用X和Z轴圆环
                2 -> listOf(0, 1) // Z轴的法线，侧面用X和Y轴圆环
                else -> listOf(0, 2) // 默认
            }
        }
    }
}