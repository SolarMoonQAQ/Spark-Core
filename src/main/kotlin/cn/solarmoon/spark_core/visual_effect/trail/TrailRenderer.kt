package cn.solarmoon.spark_core.visual_effect.trail

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.RenderTypeUtil
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class TrailRenderer: VisualEffectRenderer() {

    val meshes = mutableMapOf<String, TrailMesh>()

    fun addPoint(id: String, start: (Float) -> Vector3f, end: (Float) -> Vector3f, info: TrailInfo) {
        meshes.getOrPut(id) { TrailMesh(info) }.apply {
//            addMainPoint(TrailPoint(start(0f), end(0f), info))
//            addMainPoint(TrailPoint(start(0.25f), end(0.25f), info))
//            addMainPoint(TrailPoint(start(0.75f), end(0.75f), info))
            addMainPoint(TrailPoint(start(1f), end(1f), info))
            dynamicStart = start
            dynamicEnd = end
        }
    }

    override fun physTick(physLevel: PhysicsLevel) {}

    override fun tick() {
        meshes.values.forEach {
            it.tick()
        }
    }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        meshes.forEach { _, mesh ->
            val buffer = bufferSource.getBuffer(RenderTypeUtil.transparentRepair(mesh.info.textureLocation))
            mesh.frame(partialTicks)
            renderMesh(mesh, camPos.toVector3f(), buffer, partialTicks)
        }
    }

    private fun renderMesh(
        mesh: TrailMesh,
        camPos: Vector3f,
        buffer: VertexConsumer,
        partialTicks: Float
    ) {
        val light = LightTexture.FULL_BRIGHT
        val overlay = OverlayTexture.NO_OVERLAY

        // 使用四边形渲染（每4个顶点一个四边形）
        mesh.zipRenderPoints().forEach { element ->
            // 获取四边形四个顶点
            val v0 = element.p1.start.sub(camPos, Vector3f())
            val v1 = element.p1.end.sub(camPos, Vector3f())
            val v2 = element.p2.start.sub(camPos, Vector3f())
            val v3 = element.p2.end.sub(camPos, Vector3f())

            val uv0 = element.uv1 // A点左侧UV
            val uv1 = element.uv2 // A点右侧UV
            val uv2 = element.uv3 // B点左侧UV
            val uv3 = element.uv4 // B点右侧UV

            // 计算法线（基于四边形平面）
            val normal = Vector3f(0f, 1f, 0f)

            // 颜色
            val color1 = element.p1.getColor(partialTicks).rgb
            val color2 = element.p2.getColor(partialTicks).rgb

            // 构建四边形
            buffer.addVertex(v0.x, v0.y, v0.z)
                .setColor(color1)
                .setUv(uv0.x, uv0.y)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z)

            buffer.addVertex(v1.x, v1.y, v1.z)
                .setColor(color1)
                .setUv(uv1.x, uv1.y)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z)

            buffer.addVertex(v3.x, v3.y, v3.z)
                .setColor(color2)
                .setUv(uv3.x, uv3.y)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z)

            buffer.addVertex(v2.x, v2.y, v2.z)
                .setColor(color2)
                .setUv(uv2.x, uv2.y)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z)
        }
    }

    private fun calculateNormal(v0: Vector3f, v1: Vector3f, v2: Vector3f, v3: Vector3f): Vector3f {
        val edge1 = Vector3f(
            (v1.x - v0.x).toFloat(),
            (v1.y - v0.y).toFloat(),
            (v1.z - v0.z).toFloat()
        )
        val edge2 = Vector3f(
            (v2.x - v0.x).toFloat(),
            (v2.y - v0.y).toFloat(),
            (v2.z - v0.z).toFloat()
        )
        val normal = edge1.cross(edge2)
        return normal.normalize()
    }
}