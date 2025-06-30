package cn.solarmoon.spark_core.physics.visualizer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.entity.Entity
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import org.joml.Matrix4f
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * 冻结状态可视化器
 * 在实体周围显示冰蓝色边框表示其物理冻结状态
 */
@OnlyIn(Dist.CLIENT)
object FrozenStateVisualizer {

    private var enabled = false

    // 缓存冻结的实体，避免每帧都重新查找
    private val frozenEntities = ConcurrentHashMap<Int, Entity>()

    // 渲染距离（方块）
    private const val RENDER_DISTANCE = 64.0

    // 更新间隔（tick）
    private const val UPDATE_INTERVAL = 10
    private var updateCounter = 0

    /**
     * 启用冻结状态可视化
     */
    fun enable() {
        if (!enabled) {
            enabled = true
            NeoForge.EVENT_BUS.register(this)
        }
    }

    /**
     * 禁用冻结状态可视化
     */
    fun disable() {
        if (enabled) {
            enabled = false
            NeoForge.EVENT_BUS.unregister(this)
            frozenEntities.clear()
        }
    }

    /**
     * 切换冻结状态可视化
     * @return 切换后的状态
     */
    fun toggle(): Boolean {
        if (enabled) disable() else enable()
        return enabled
    }

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (!enabled || event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera
        val cameraPos = camera.position

        val poseStack = event.poseStack
        val bufferSource = minecraft.renderBuffers().bufferSource()

        // 渲染所有冻结实体的边框
        frozenEntities.values.forEach { entity ->
            renderFrozenEntityBox(entity, poseStack, bufferSource, cameraPos.x, cameraPos.y, cameraPos.z)
        }

        bufferSource.endBatch()
    }


    /**
     * 渲染冻结实体的边框
     */
    private fun renderFrozenEntityBox(
        entity: Entity,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camX: Double,
        camY: Double,
        camZ: Double
    ) {
        poseStack.pushPose()

        // 获取实体边界盒
        val box = entity.boundingBox.inflate(0.05)

        // 冰蓝色
        val color = Color(0, 191, 255, 128)

        // 渲染边界盒
        LevelRenderer.renderLineBox(
            poseStack,
            bufferSource.getBuffer(RenderType.lines()),
            box.move(-camX, -camY, -camZ),
            color.red / 255f,
            color.green / 255f,
            color.blue / 255f,
            color.alpha / 255f
        )

        // 在实体上方渲染冰晶图标
        renderFrozenIcon(entity, poseStack, bufferSource, camX, camY, camZ)

        poseStack.popPose()
    }

    /**
     * 在实体上方渲染冰晶图标
     */
    private fun renderFrozenIcon(
        entity: Entity,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camX: Double,
        camY: Double,
        camZ: Double
    ) {
        poseStack.pushPose()

        // 设置渲染位置为实体上方
        val x = entity.x - camX
        val y = entity.y + entity.boundingBox.ysize + 0.5 - camY
        val z = entity.z - camZ

        poseStack.translate(x, y, z)

        // 使图标始终面向玩家
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        poseStack.mulPose(camera.rotation())
        poseStack.scale(0.5f, 0.5f, 0.5f)

        // 渲染简单的冰晶图形
        val matrix = poseStack.last().pose()
        val buffer = bufferSource.getBuffer(RenderType.lines())

        // 冰蓝色
        val r = 0f
        val g = 191f / 255f
        val b = 1f
        val a = 0.8f

        // 渲染六角雪花
        renderSnowflake(matrix, buffer, r, g, b, a)

        poseStack.popPose()
    }

    /**
     * 渲染六角雪花图案
     */
    private fun renderSnowflake(matrix: Matrix4f, buffer: com.mojang.blaze3d.vertex.VertexConsumer, r: Float, g: Float, b: Float, a: Float) {
        val size = 0.5f

        // 计算颜色的packed值
        val packedColor = (((a * 255).toInt() and 0xFF) shl 24) or
                         (((r * 255).toInt() and 0xFF) shl 16) or
                         (((g * 255).toInt() and 0xFF) shl 8) or
                         ((b * 255).toInt() and 0xFF)

        // 设置默认的光照和覆盖值
        val packedLight = 0xF000F0 // 最大亮度
        val packedOverlay = 0 // 无覆盖

        // 渲染六条主轴
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60).toDouble()).toFloat()
            val x = size * cos(angle.toDouble()).toFloat()
            val y = size * sin(angle.toDouble()).toFloat()

            // 主轴起点
            buffer.addVertex(0f, 0f, 0f, packedColor, 0f, 0f, packedOverlay, packedLight, 0f, 1f, 0f)
            // 主轴终点
            buffer.addVertex(x, y, 0f, packedColor, 0f, 0f, packedOverlay, packedLight, 0f, 1f, 0f)

            // 每个主轴上的分支
            val branchSize = size * 0.4f
            val branchAngle1 = angle + Math.toRadians(30.0).toFloat()
            val branchAngle2 = angle - Math.toRadians(30.0).toFloat()

            val midX = x * 0.6f
            val midY = y * 0.6f

            val branch1X = midX + branchSize * cos(branchAngle1.toDouble()).toFloat()
            val branch1Y = midY + branchSize * sin(branchAngle1.toDouble()).toFloat()

            val branch2X = midX + branchSize * cos(branchAngle2.toDouble()).toFloat()
            val branch2Y = midY + branchSize * sin(branchAngle2.toDouble()).toFloat()

            // 分支1
            buffer.addVertex(midX, midY, 0f, packedColor, 0f, 0f, packedOverlay, packedLight, 0f, 1f, 0f)
            buffer.addVertex(branch1X, branch1Y, 0f, packedColor, 0f, 0f, packedOverlay, packedLight, 0f, 1f, 0f)

            // 分支2
            buffer.addVertex(midX, midY, 0f, packedColor, 0f, 0f, packedOverlay, packedLight, 0f, 1f, 0f)
            buffer.addVertex(branch2X, branch2Y, 0f, packedColor, 0f, 0f, packedOverlay, packedLight, 0f, 1f, 0f)
        }
    }
} 