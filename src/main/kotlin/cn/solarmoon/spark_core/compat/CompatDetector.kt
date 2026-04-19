package cn.solarmoon.spark_core.compat

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.compat.accelerated_rendering.ARCompat
import cn.solarmoon.spark_core.compat.create.CreateCompat
import cn.solarmoon.spark_core.compat.first_person_model.FirstPersonModelCompat
import cn.solarmoon.spark_core.compat.player_animator.PlayerAnimatorCompat
import cn.solarmoon.spark_core.compat.real_camera.RealCameraCompat
import cn.solarmoon.spark_core.compat.sodium.SodiumCompat
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent

/**
 * 检测意图兼容的Mod是否被加载
 */
@EventBusSubscriber(modid = SparkCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object CompatDetector {

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        SodiumCompat.init()
        RealCameraCompat.init()
        PlayerAnimatorCompat.init()
        FirstPersonModelCompat.init()
        ARCompat.init()
        // 在统一兼容检测阶段初始化 Create 兼容入口。
        // 这里只做“是否加载”的探测，具体兼容逻辑由各功能模块按需调用 CreateCompat.whenLoaded 执行。
        CreateCompat.init()
    }
}
