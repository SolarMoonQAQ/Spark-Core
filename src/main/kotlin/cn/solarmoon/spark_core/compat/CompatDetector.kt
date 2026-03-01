package cn.solarmoon.spark_core.compat

import cn.solarmoon.spark_core.SparkCore
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
    }
}