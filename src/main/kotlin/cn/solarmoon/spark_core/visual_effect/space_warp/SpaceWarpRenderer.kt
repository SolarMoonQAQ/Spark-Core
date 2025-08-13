package cn.solarmoon.spark_core.visual_effect.space_warp

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.registry.client.SparkShaders
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.tan

class SpaceWarpRenderer : VisualEffectRenderer() {
    private val warps = ConcurrentLinkedQueue<SpaceWarp>()

    private var offA: TextureTarget? = null
    private var offB: TextureTarget? = null
    private var lastW = -1
    private var lastH = -1
    private var time = 0f

    fun add(w: SpaceWarp) {
        warps.add(w)
    }

    /**
     * 服务端广播添加（动态版：仅同步最大半径/初始强度/生命周期/脉动频率）
     */
    fun addToClient(
        pos: Vec3,
        maxRadiusMeters: Float,
        baseStrength: Float,
        lifeTime: Int,
        pulseHz: Float = 0f
    ) {
        PacketDistributor.sendToAllPlayers(
            SpaceWarpPayload(pos, maxRadiusMeters, baseStrength, lifeTime, pulseHz)
        )
    }

    override fun tick() {
        val it = warps.iterator()
        while (it.hasNext()) {
            val w = it.next()
            w.tick()
            if (!w.alive) it.remove()
        }
    }

    override fun physTick(physLevel: PhysicsLevel) { /* no-op */ }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        val shader = SparkShaders.DISTORT_SHADER ?: return
        if (warps.isEmpty()) return

        val main = mc.mainRenderTarget
        val w = main.width
        val h = main.height
        ensureOffscreens(w, h)

        time += partialTicks

        // 1) 主屏复制到 A
        offA!!.bindWrite(true)
        RenderSystem.viewport(0, 0, w, h)
        drawFullscreen(shader, main.colorTextureId, Vector2f(0f, 0f), 0f, 0f, 0f, time, w, h, copyOnly = true)

        // 2) 计算可见波圈
        val cam = mc.gameRenderer.mainCamera
        val aspect = w.toFloat() / h.toFloat()
        val fovDegrees = mc.gameRenderer.getFov(cam, partialTicks, false).toFloat()

        val activeWarps = mutableListOf<WarpDraw>()
        warps.forEach { warp ->
            projectToScreen(warp.pos, cam, camPos, fovDegrees, aspect, w, h)?.let { (centerPx, camSpaceZ) ->
                val radiusPx = worldRadiusToPixels(warp.radiusNow(), camSpaceZ, fovDegrees, h)
                val bandWidthPx = worldRadiusToPixels(warp.bandWidthNow(), camSpaceZ, fovDegrees, h)
                val maxRadiusPx = worldRadiusToPixels(warp.maxRadiusMeters, camSpaceZ, fovDegrees, h)

                // 裁剪：太小看不见；超过最大半径后跳过
                if (radiusPx > 1f && radiusPx <= (maxRadiusPx + bandWidthPx)) {
                    activeWarps.add(WarpDraw(centerPx, radiusPx, bandWidthPx, warp.strengthNow()))
                }
            }
        }

        if (activeWarps.isEmpty()) {
            // 无可见波圈 → 直接把 A 复制回主屏
            main.bindWrite(true)
            RenderSystem.viewport(0, 0, w, h)
            drawFullscreen(shader, offA!!.colorTextureId, Vector2f(0f, 0f), 0f, 0f, 0f, time, w, h, copyOnly = true)
            return
        }

        // 3) 所有波圈统一处理：从原始画面(A)读取，直接输出到主屏
        main.bindWrite(true)
        RenderSystem.viewport(0, 0, w, h)

        // 首先绘制原始画面
        drawFullscreen(shader, offA!!.colorTextureId, Vector2f(0f, 0f), 0f, 0f, 0f, time, w, h, copyOnly = true)

        for (wd in activeWarps) {
            drawFullscreen(
                shader = shader,
                srcTexId = offA!!.colorTextureId,  // 始终从原始画面读取
                centerPx = wd.centerPx,
                radiusPx = wd.waveRadiusPx,
                bandWidthPx = wd.bandWidthPx,
                strength = wd.strength,
                time = time,
                w = w,
                h = h,
                copyOnly = false
            )
        }
    }

    private data class WarpDraw(
        val centerPx: Vector2f,
        val waveRadiusPx: Float,
        val bandWidthPx: Float,
        val strength: Float
    )

    private fun ensureOffscreens(w: Int, h: Int) {
        if (offA == null || offB == null || w != lastW || h != lastH) {
            offA?.destroyBuffers(); offB?.destroyBuffers()
            offA = TextureTarget(w, h, true, Minecraft.ON_OSX).apply {
                setClearColor(0f, 0f, 0f, 0f); createBuffers(w, h, true)
            }
            offB = TextureTarget(w, h, true, Minecraft.ON_OSX).apply {
                setClearColor(0f, 0f, 0f, 0f); createBuffers(w, h, true)
            }
            lastW = w; lastH = h
        }
    }

    /**
     * copyOnly=false 时会设置波圈参数（Radius=波前当前位置，BandWidth=厚度）
     */
    private fun drawFullscreen(
        shader: ShaderInstance,
        srcTexId: Int,
        centerPx: Vector2f,
        radiusPx: Float,
        bandWidthPx: Float,
        strength: Float,
        time: Float,
        w: Int,
        h: Int,
        copyOnly: Boolean
    ) {
        try {
            RenderSystem.setShader { shader }
            RenderSystem.setShaderTexture(0, srcTexId)

            shader.safeGetUniform("ScreenSize").set(w.toFloat(), h.toFloat())

            if (copyOnly) {
                // 让 shader 走“原样拷贝”路径：强度=0
                shader.safeGetUniform("Center").set(0f, 0f)
                shader.safeGetUniform("Radius").set(0f)
                shader.safeGetUniform("BandWidth").set(0f)
                shader.safeGetUniform("Strength").set(0f)
            } else {
                shader.safeGetUniform("Center").set(centerPx.x, centerPx.y)
                shader.safeGetUniform("Radius").set(radiusPx)
                shader.safeGetUniform("BandWidth").set(bandWidthPx)
                shader.safeGetUniform("Strength").set(strength)
            }
            shader.safeGetUniform("Time").set(time)

            val t = Tesselator.getInstance()
            val bb: BufferBuilder = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            bb.addVertex(-1f, -1f, 0f).setUv(0f, 0f)
            bb.addVertex( 1f, -1f, 0f).setUv(1f, 0f)
            bb.addVertex( 1f,  1f, 0f).setUv(1f, 1f)
            bb.addVertex(-1f,  1f, 0f).setUv(0f, 1f)
            BufferUploader.drawWithShader(bb.build()!!)
        } finally {

        }
    }

    /**
     * 将世界坐标投影到屏幕像素坐标，返回 Pair(centerPx, cameraSpaceZ)
     * cameraSpaceZ 为相机空间 Z（通常前方为负值）
     */
    private fun projectToScreen(
        world: Vec3,
        cam: net.minecraft.client.Camera,
        camPos: Vec3,
        fovDeg: Float,
        aspect: Float,
        width: Int,
        height: Int
    ): Pair<Vector2f, Float>? {
        val rel = Vector3f(
            (world.x - camPos.x).toFloat(),
            (world.y - camPos.y).toFloat(),
            (world.z - camPos.z).toFloat()
        )
        // 相机旋转的共轭，把世界向量转到相机空间
        val invRot = Quaternionf(cam.rotation()).conjugate()
        rel.rotate(invRot)

        val z = rel.z // 相机空间 Z（面向前通常为负）
        if (z >= -0.05f) return null // 近似剔除：太靠近或在后方

        val fovRad = Math.toRadians(fovDeg.toDouble()).toFloat()
        val yScale = (1f / tan(fovRad / 2f))
        val xScale = yScale / aspect

        val ndcX = (rel.x / -z) * xScale
        val ndcY = (rel.y / -z) * yScale

        val px = ((ndcX * 0.5f) + 0.5f) * width
        val py = (1f - ((ndcY * 0.5f) + 0.5f)) * height

        return Vector2f(px, py) to z
    }

    /**
     * 世界尺度（米） -> 像素尺度的线性近似（基于垂直 FOV 与深度）
     */
    private fun worldRadiusToPixels(
        radiusMeters: Float,
        camSpaceZ: Float,     // 负值（前方）
        fovDeg: Float,
        screenHeight: Int
    ): Float {
        val fovRad = Math.toRadians(fovDeg.toDouble()).toFloat()
        val pixelsPerMeterAt1m = screenHeight / (2f * tan(fovRad / 2f))
        val metersPerDepth = -camSpaceZ
        return radiusMeters * (pixelsPerMeterAt1m / metersPerDepth)
    }
}
