package cn.solarmoon.spark_core.visual_effect.common.geom

import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Vector3f
import org.ode4j.ode.DBox
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * 控制box在debug中的渲染
 */
class GeomRenderer(): VisualEffectRenderer() {

    val renderableBoxes = ConcurrentHashMap<String, RenderableGeom>()

    /**
     * 获得已有的可渲染box，如果不存在则会创建一个新的
     */
    fun getRenderableBox(id: String): RenderableGeom {
        return renderableBoxes.computeIfAbsent(id) { RenderableGeom() }
    }

    /**
     * 将指定id的box渲染同步到客户端，其中color和box都为可选，只填入其中一个，另一个为null时将只同步不为null的内容
     */
    fun syncBoxToClient(id: String?, color: Color?, box: Int?) {
        if (id == null) return
        PacketDistributor.sendToAllPlayers(RenderableGeomPayload(id, color?.rgb, box))
    }

    /**
     * 客户端每tick更新要渲染的box
     */
    override fun tick() {
        val iterator = renderableBoxes.entries.iterator()
        while (iterator.hasNext()) {
            val (_, value) = iterator.next()
            value.tick()
            if (value.isRemoved) {
                iterator.remove()
            }
        }
    }

    override fun render(mc: Minecraft, camPos: Vec3, poseStack: PoseStack, bufferSource: MultiBufferSource, partialTicks: Float) {
        val buffer = bufferSource.getBuffer(RenderType.LINES)
        if (!mc.entityRenderDispatcher.shouldRenderHitBoxes()) {
            renderableBoxes.clear()
            return
        }
        renderableBoxes.forEach { id, manager ->
            if (manager.currentBox?.isEnabled == false) return@forEach
            val box = manager.getBox(partialTicks) ?: return@forEach
            if (box is DBox) {
                box.orderedVertexes.zipWithNext { v1, v2 ->
                    val normal = Vector3f(v2.x.toFloat() - v1.x.toFloat(), v2.y.toFloat() - v1.y.toFloat(), v2.z.toFloat() - v1.z.toFloat()).normalize()
                    val color = manager.color
                    buffer.addVertex(poseStack.last().pose(), v1.x.toFloat() - camPos.x.toFloat(), v1.y.toFloat() - camPos.y.toFloat(), v1.z.toFloat() - camPos.z.toFloat())
                        .setColor(color.red, color.green, color.blue, color.alpha)
                        .setNormal(poseStack.last(), normal.x, normal.y, normal.z) // 法线方向，影响光照，将会随着与光线夹角越小亮度越大
                    buffer.addVertex(poseStack.last().pose(), v2.x.toFloat() - camPos.x.toFloat(), v2.y.toFloat() - camPos.y.toFloat(), v2.z.toFloat() - camPos.z.toFloat())
                        .setColor(color.red, color.green, color.blue, color.alpha)
                        .setNormal(poseStack.last(), normal.x, normal.y, normal.z)
                }
            }
        }
    }

}