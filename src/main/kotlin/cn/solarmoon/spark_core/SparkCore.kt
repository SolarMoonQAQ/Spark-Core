package cn.solarmoon.spark_core

import cn.solarmoon.spark_core.entry_builder.ObjectRegister
import cn.solarmoon.spark_core.molang.core.MolangParser
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
        val LOGGER = LoggerFactory.getLogger("星火核心")
        val REGISTER = ObjectRegister(MOD_ID, true)
        val REGISTER_MM = ObjectRegister("machine_max", false)
        @JvmField
        val PARSER = MolangParser(HashMap(4))

        fun logger(suffix: String): Logger = LoggerFactory.getLogger("星火核心/$suffix")
    }

    init {
        SparkPackResourceLoader.loadAllModules()
        NativeLoader.load("bullet", selectLib())

        REGISTER.register(modEventBus)
        REGISTER_MM.register(modEventBus)
        SparkRegistries.register()

        if (FMLEnvironment.dist.isClient) {
            SparkClientEventRegister.register()
            SparkModelRegister.register(modEventBus)
            SparkKeyMappings.register()
            SparkShaders.register(modEventBus)
            SparkParticleProviderRegister.register(modEventBus)
        }

        SparkJSScriptRegister.register(modEventBus)
        SparkVisualEffects.register()
        SparkAttachments.register()
        SparkCommonEventRegister.register(modEventBus)
        SparkPayloadRegister.register(modEventBus)
        SparkTypedAnimations.register()
        SparkDataComponents.register()
        SparkDataRegistryRegister.register(modEventBus)
        SparkCodeRegister.register(modEventBus)
        SparkCommandRegister.register()
        SparkSyncerTypes.register()
        SparkDataGenerator.register(modEventBus)
        SparkCapabilities.register()
        SparkParticles.register()
        SparkPackModuleRegister.register(modEventBus)
        SparkStateMachineRegister.register()
    }
}
