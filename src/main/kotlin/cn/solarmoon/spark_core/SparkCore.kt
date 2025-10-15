package cn.solarmoon.spark_core

import cn.solarmoon.spark_core.entry_builder.ObjectRegister
import cn.solarmoon.spark_core.pack.NativeLoader
import cn.solarmoon.spark_core.pack.SparkPackResourceLoader
import cn.solarmoon.spark_core.physics.selectLib
import cn.solarmoon.spark_core.registry.client.*
import cn.solarmoon.spark_core.registry.common.*
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(SparkCore.MOD_ID)
class SparkCore(modEventBus: IEventBus, modContainer: ModContainer) {

    companion object {
        const val MOD_ID = "spark_core"
        @JvmField
        val LOGGER = logger()
        val REGISTER = ObjectRegister(MOD_ID)
        lateinit var EVENT_BUS: IEventBus
            private set

        fun logger(suffix: String = ""): Logger = LoggerFactory.getLogger("星火核心/$suffix")
    }

    init {
        EVENT_BUS = modEventBus
        SparkPackResourceLoader.loadAllModules()
        NativeLoader.load("bullet", selectLib())
        REGISTER.register(modEventBus)

        if (FMLEnvironment.dist.isClient) {
            SparkClientEventRegister.register()
            SparkModelRegister.register(modEventBus)
            SparkShaders.register(modEventBus)
            SparkParticleProviderRegister.register(modEventBus)
        }

        SparkRegistries.register()
        SparkVisualEffects.register()
        SparkCommonEventRegister.register(modEventBus)
        SparkPayloadRegister.register(modEventBus)
        SparkDataComponents.register()
        SparkCodeRegister.register(modEventBus)
        SparkCommandRegister.register()
        SparkSyncerTypes.register()
        SparkCapabilities.register()
        SparkParticles.register()
        SparkPackModuleRegister.register(modEventBus)
        SparkStateMachineRegister.register()
    }

}
