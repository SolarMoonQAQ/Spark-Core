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
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent

/**
 * 检测意图兼容的Mod是否被加载
 */
@EventBusSubscriber(modid = SparkCore.MOD_ID)
object CompatDetector {

    /**
     * 通用初始化阶段（客户端 + 服务端）。
     *
     * 这里初始化 Create 兼容，以便双端都能注册装置物理联动逻辑。
     */
    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        CreateCompat.init()
    }

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        SodiumCompat.init()
        RealCameraCompat.init()
        PlayerAnimatorCompat.init()
        FirstPersonModelCompat.init()
        ARCompat.init()
    }
}
