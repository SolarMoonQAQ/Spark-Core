package cn.solarmoon.spark_core.visual_effect.space_warp

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.RenderTypeUtil
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.ConcurrentLinkedQueue

class SpaceWarpRenderer : VisualEffectRenderer() {

    private val warps = ConcurrentLinkedQueue<SpaceWarp>()

    fun add(w: SpaceWarp) {
        warps.add(w)
    }

    fun addToClient(
        pos: Vec3,
        minRadius: Float,
        maxRadius: Float,
        baseStrength: Float,
        lifeTime: Int,
    ) {
        PacketDistributor.sendToAllPlayers(
            SpaceWarpPayload(pos, minRadius, maxRadius, baseStrength, lifeTime)
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

    override fun physTick(physLevel: PhysicsLevel) { }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        if (warps.isEmpty) return
        warps.forEach {
            // 固定立方体中心在世界原点 (0, 0, 0)，边长 2
            val buffer = bufferSource.getBuffer(RenderTypeUtil.pureEffect(it.getProgress(partialTicks), it.getStrength(partialTicks)))
            val rPos = it.pos.subtract(camPos).toVector3f()
            val cx = rPos.x
            val cy = rPos.y
            val cz = rPos.z
            val r = it.getRadius(partialTicks) // 半径

            fun addVertex(poseStack: PoseStack.Pose, x: Float, y: Float, z: Float): VertexConsumer {
                return buffer.addVertex(x, y, z)
            }

            // 六个面，每面四个顶点
            // 前面 (z = -r)
            addVertex(poseStack.last(), cx - r, cy - r, cz - r).setUv(0f, 0f)
            addVertex(poseStack.last(), cx + r, cy - r, cz - r).setUv(1f, 0f)
            addVertex(poseStack.last(), cx + r, cy + r, cz - r).setUv(1f, 1f)
            addVertex(poseStack.last(), cx - r, cy + r, cz - r).setUv(0f, 1f)

            // 后面 (z = +r)
            addVertex(poseStack.last(), cx + r, cy - r, cz + r).setUv(0f, 0f)
            addVertex(poseStack.last(), cx - r, cy - r, cz + r).setUv(1f, 0f)
            addVertex(poseStack.last(), cx - r, cy + r, cz + r).setUv(1f, 1f)
            addVertex(poseStack.last(), cx + r, cy + r, cz + r).setUv(0f, 1f)

            // 左面 (x = -r)
            addVertex(poseStack.last(), cx - r, cy - r, cz + r).setUv(0f, 0f)
            addVertex(poseStack.last(), cx - r, cy - r, cz - r).setUv(1f, 0f)
            addVertex(poseStack.last(), cx - r, cy + r, cz - r).setUv(1f, 1f)
            addVertex(poseStack.last(), cx - r, cy + r, cz + r).setUv(0f, 1f)

            // 右面 (x = +r)
            addVertex(poseStack.last(), cx + r, cy - r, cz - r).setUv(0f, 0f)
            addVertex(poseStack.last(), cx + r, cy - r, cz + r).setUv(1f, 0f)
            addVertex(poseStack.last(), cx + r, cy + r, cz + r).setUv(1f, 1f)
            addVertex(poseStack.last(), cx + r, cy + r, cz - r).setUv(0f, 1f)

            // 上面 (y = +r)
            addVertex(poseStack.last(), cx - r, cy + r, cz - r).setUv(0f, 0f)
            addVertex(poseStack.last(), cx + r, cy + r, cz - r).setUv(1f, 0f)
            addVertex(poseStack.last(), cx + r, cy + r, cz + r).setUv(1f, 1f)
            addVertex(poseStack.last(), cx - r, cy + r, cz + r).setUv(0f, 1f)

            // 下面 (y = -r)
            addVertex(poseStack.last(), cx - r, cy - r, cz + r).setUv(0f, 0f)
            addVertex(poseStack.last(), cx + r, cy - r, cz + r).setUv(1f, 0f)
            addVertex(poseStack.last(), cx + r, cy - r, cz - r).setUv(1f, 1f)
            addVertex(poseStack.last(), cx - r, cy - r, cz - r).setUv(0f, 1f)
        }
    }


}
