package cn.solarmoon.spark_core.ik.visualizer

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.IKComponent
import cn.solarmoon.spark_core.ik.component.IKHost
import cn.solarmoon.spark_core.physics.level.ClientPhysicsLevel
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f

import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.math.Vector3f
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.ArrayList

/**
 * Renders debug visualizations for IK components (rays, target points).
 * This runs on the client side and performs its own raycast for visualization purposes.
 */
class IKDebugRenderer : VisualEffectRenderer() {

    var isEnabled: Boolean = false // Toggle to enable/disable rendering

    // Define colors
    private val COLOR_RAY = ColorRGBA(0.8f, 0.8f, 0.8f, 0.8f) // Grey for ray
    private val COLOR_DESIRED = ColorRGBA(0f, 0f, 1f, 1f)    // Blue for desired target
    private val COLOR_ACTUAL_GROUNDED = ColorRGBA(0f, 1f, 0f, 1f) // Green for actual target (grounded)
    private val COLOR_ACTUAL_AIR = ColorRGBA(1f, 0f, 0f, 1f)    // Red for actual target (not grounded)

    private val TARGET_BOX_SIZE = 0.05f // Size of the debug box for targets

    override fun tick() {
    }

    override fun physTick(physLevel: PhysicsLevel) {
    }

    override fun render(
        mc: Minecraft,
        cameraPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        tickDelta: Float
    ) {
        if (!isEnabled) return

        val player = Minecraft.getInstance().player ?: return
        if (player !is IKHost<*>) return // Ensure player implements IKHost

        val ikManager = player.ikManager // Should be implemented now
        val physicsWorld = player.physicsLevel.world // Get client physics world

        val buffer = bufferSource.getBuffer(RenderType.lines()) // Use line renderer

        // Important: Translate poseStack so world coordinates become relative to camera
        poseStack.pushPose()
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
        val matrix = poseStack.last().pose() // Get the final matrix for drawing

        for (component in ikManager.activeComponents) {
            if (!component.value.stickToGround) continue // Only visualize components trying to stick

            // --- Re-calculate desired target on client ---
            // We need the *animated* position on the client.
            // Assuming IAnimatable provides this via getWorldBonePivot
            val desiredTargetWorldJME: Vector3f = try {
                player.getWorldBonePivot(component.value.type.endBoneName, Vec3.ZERO, tickDelta)
            } catch (e: Exception) {
                SparkCore.LOGGER.warn("IKDebug: Failed to get world bone pivot for ${component.value.type.endBoneName}", e)
                continue // Skip if we can't get the animated position
            }.toBVector3f()

            // --- Perform client-side raycast for visualization ---
            val rayStart = desiredTargetWorldJME.add(component.value.groundCheckOffset)
            val rayEnd = rayStart.subtract(0f, component.value.groundCheckDistance, 0f)
            val results = ArrayList<PhysicsRayTestResult>()
            physicsWorld.rayTest(rayStart, rayEnd, results) // Use the available rayTest

            var closestHit: PhysicsRayTestResult? = null
            var minHitFraction = Float.MAX_VALUE
            var clientActualTargetPos = desiredTargetWorldJME // Default to desired if no hit
            var clientIsGrounded = false

            for (result in results) {
                val collisionObject = result.collisionObject
                // Use the same filtering as in IKComponent
                if (collisionObject != null && collisionObject.isStatic && collisionObject.collisionGroup == 1) {
                    if (result.hitFraction < minHitFraction) {
                        minHitFraction = result.hitFraction
                        closestHit = result
                    }
                }
            }

            if (closestHit != null) {
                // Calculate hit point manually
                val hitDir = rayEnd.subtract(rayStart)
                clientActualTargetPos = rayStart.add(hitDir.multLocal(closestHit.hitFraction))
                clientIsGrounded = true
            }

            // --- Draw Visualizations ---
            // 1. Draw the Ray
            drawLine(matrix, buffer, rayStart, rayEnd, COLOR_RAY)

            // 2. Draw the Desired Target Point (Blue Box)
            drawWireBox(matrix, buffer, desiredTargetWorldJME, TARGET_BOX_SIZE, COLOR_DESIRED)

            // 3. Draw the Actual Target Point (Green/Red Box)
            val actualColor = if (clientIsGrounded) COLOR_ACTUAL_GROUNDED else COLOR_ACTUAL_AIR
            drawWireBox(matrix, buffer, clientActualTargetPos, TARGET_BOX_SIZE, actualColor)
        }

        poseStack.popPose() // Restore pose stack
    }

    // --- Embedded Drawing Logic ---

    /** Simple RGBA color representation */
    private data class ColorRGBA(val r: Float, val g: Float, val b: Float, val a: Float)

    /** Draws a single line segment using the provided matrix and buffer. */
    private fun drawLine(matrix: Matrix4f, buffer: VertexConsumer, start: Vector3f, end: Vector3f, color: ColorRGBA) {
        buffer.addVertex(matrix, start.x, start.y, start.z)
            .setColor(color.r, color.g, color.b, color.a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(matrix, end.x, end.y, end.z)
            .setColor(color.r, color.g, color.b, color.a)
            .setNormal(0f, 1f, 0f)
    }

    /** Draws a wireframe box centered at 'center' with uniform half-size 'size'. */
    private fun drawWireBox(matrix: Matrix4f, buffer: VertexConsumer, center: Vector3f, size: Float, color: ColorRGBA) {
        val halfSize = size // Parameter 'size' is already half-extents
        val minX = center.x - halfSize
        val minY = center.y - halfSize
        val minZ = center.z - halfSize
        val maxX = center.x + halfSize
        val maxY = center.y + halfSize
        val maxZ = center.z + halfSize

        // Define the 8 corners
        val p0 = Vector3f(minX, minY, minZ); val p1 = Vector3f(maxX, minY, minZ)
        val p2 = Vector3f(maxX, minY, maxZ); val p3 = Vector3f(minX, minY, maxZ)
        val p4 = Vector3f(minX, maxY, minZ); val p5 = Vector3f(maxX, maxY, minZ)
        val p6 = Vector3f(maxX, maxY, maxZ); val p7 = Vector3f(minX, maxY, maxZ)

        // Draw the 12 edges (Bottom, Top, Verticals)
        drawLine(matrix, buffer, p0, p1, color); drawLine(matrix, buffer, p1, p2, color)
        drawLine(matrix, buffer, p2, p3, color); drawLine(matrix, buffer, p3, p0, color)
        drawLine(matrix, buffer, p4, p5, color); drawLine(matrix, buffer, p5, p6, color)
        drawLine(matrix, buffer, p6, p7, color); drawLine(matrix, buffer, p7, p4, color)
        drawLine(matrix, buffer, p0, p4, color); drawLine(matrix, buffer, p1, p5, color)
        drawLine(matrix, buffer, p2, p6, color); drawLine(matrix, buffer, p3, p7, color)
    }
}