package cn.solarmoon.spark_core.visual_effect.common.trail

import cn.solarmoon.spark_core.util.RenderTypeUtil
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3
import java.util.concurrent.ConcurrentLinkedQueue

class TrailRenderer: VisualEffectRenderer() {

    private val trails = hashMapOf<String, MutableList<Trail>>()
    private val trailTemplate = mutableMapOf<String, (Float) -> Trail>()

    fun refresh(id: String, trail: (Float) -> Trail) {
        trailTemplate[id] = trail
    }

    override fun tick() {
        trails.values.forEach {
            it.forEach {
                it.tick()
            }
        }

        trailTemplate.clear()
        trails.values.forEach { it.removeIf { it.isRemoved } }
    }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        trailTemplate.forEach { (id, trail) ->
            trails.getOrPut(id) { mutableListOf() }.add(trail.invoke(partialTicks))
        }

        // 对缓存的拖影进行渲染，显然，越新的拖影越在列表之后，因此可用当前序列和下一个序列的拖影组成单位长方形，如此混合便可以遍历全部轨迹（也就是分成了微元）
        trails.values.forEach {
            it.forEachIndexed { index, trail ->
                // 四个点组成单位长方形
                val dT1 = it.elementAt(index)
                val dT2 = if (index + 1 < it.size) it.elementAt(index + 1) else it.elementAt(index)
                val pot1s = dT1.start.toVec3().subtract(camPos).toVector3f(); val pot1e = dT1.end.toVec3().subtract(camPos).toVector3f()
                val pot2s = dT2.start.toVec3().subtract(camPos).toVector3f(); val pot2e = dT2.end.toVec3().subtract(camPos).toVector3f()
                val color = trail.color.getColorComponents(null)
                val light = LightTexture.FULL_BRIGHT
                val overlay = OverlayTexture.NO_OVERLAY
                fun p(p: Trail): Float = 1 - p.getProgress(partialTicks)
                val normal = Vector3f(0f, 1f, 0f).normalize()
                val buffer = bufferSource.getBuffer(RenderTypeUtil.transparentRepair(trail.getTexture()))
                buffer.addVertex(pot1s.x, pot1s.y, pot1s.z).setColor(color[0], color[1], color[2], p(dT1)).setLight(light).setUv(1f, 0f).setOverlay(overlay).setNormal(normal.x, normal.y, normal.z)
                buffer.addVertex(pot1e.x, pot1e.y, pot1e.z).setColor(color[0], color[1], color[2], p(dT1)).setLight(light).setUv(0f, 1f).setOverlay(overlay).setNormal(normal.x, normal.y, normal.z)
                buffer.addVertex(pot2e.x, pot2e.y, pot2e.z).setColor(color[0], color[1], color[2], p(dT2)).setLight(light).setUv(0f, 1f).setOverlay(overlay).setNormal(normal.x, normal.y, normal.z)
                buffer.addVertex(pot2s.x, pot2s.y, pot2s.z).setColor(color[0], color[1], color[2], p(dT2)).setLight(light).setUv(1f, 0f).setOverlay(overlay).setNormal(normal.x, normal.y, normal.z)
            }
        }
    }

}
