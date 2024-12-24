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

    private val autoAdd: MutableMap<String, (Float) -> Trail?> = mutableMapOf()
    private val addSave: MutableMap<String, Boolean> = mutableMapOf()
    private val trails = ConcurrentLinkedQueue<Trail>()
    private val removeT = ConcurrentLinkedQueue<Trail>()

    /**
     * 为了保证拖影的连贯性，使用[add]方法根据partialTicks来在渲染tick中不断地刷新拖影顶点，而不是在游戏tick中添加间断地添加顶点，以达到近似圆的平滑效果
     *
     * **此方法只能在客户端侧调用**
     * @param toggle toggle为false的情况下，在拖影渲染后会立即清除拖影列表，为true时则只有当调用[clear]方法时才会清除拖影
     */
    fun setAdd(id: String, toggle: Boolean = false, add: (Float) -> Trail? = { null }) {
        autoAdd[id] = add
        if (toggle) addSave[id] = true
    }

    fun clear(id: String) {
        addSave[id] = false
    }

    override fun tick() {
        // 基本tick，让其流转到生命周期结束
        trails.forEach { trail ->
            trail.tick()
            if (trail.isFinished) {
                removeT.add(trail)
            }
        }
        // 延迟结算，比较高效
        trails.removeAll(removeT)
        removeT.clear()

        // 如果是开关性质的则为true时不删除
        autoAdd.entries.removeIf {
            val toggle = addSave[it.key]
            toggle == null || !toggle
        }
    }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        autoAdd.toMap().forEach { id, add ->
            add.invoke(partialTicks)?.let {
                trails.add(it)
            }
        }

        // 对缓存的拖影进行渲染，显然，越新的拖影越在列表之后，因此可用当前序列和下一个序列的拖影组成单位长方形，如此混合便可以遍历全部轨迹（也就是分成了微元）
        trails.toList().forEachIndexed { index, trail ->
            // 四个点组成单位长方形
            val dT1 = trails.elementAt(index)
            val dT2 = if (index + 1 < trails.size) trails.elementAt(index + 1) else trails.elementAt(index)
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
