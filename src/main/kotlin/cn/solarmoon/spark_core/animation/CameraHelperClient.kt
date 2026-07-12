package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ViewportEvent

/**
 * ### 客户端相机数据更新器
 *
 * 监听 `ViewportEvent.ComputeFov`（LOWEST 优先级）以获取包含其他模组修改后的最终 FOV 值，
 * 同时从同一事件中获取相机位置，写入 [CameraHelper] 的 volatile 字段。
 *
 * **事件选择**：`ComputeFov` 携带 `Camera` 对象，可同时获取 FOV 和相机位置，减少事件监听数量。
 * **LOWEST 优先级**：确保取到包含其他模组（如枪械模组的瞄准镜 FOV 修改）的最终值。
 * **一 tick 延迟**：物理线程在下一次步进（~16.67ms）后才读取这些 volatile 值，LOD 切换存在至多一 tick 延迟。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SparkCore.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object CameraHelperClient {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onComputeFov(event: ViewportEvent.ComputeFov) {
        CameraHelper.fov = event.fov
        CameraHelper.cameraPos = event.camera.position

        val mc = Minecraft.getInstance()
        CameraHelper.screenHeight = mc.window.height
        CameraHelper.renderDistance = mc.gameRenderer.renderDistance.toDouble()
    }
}
