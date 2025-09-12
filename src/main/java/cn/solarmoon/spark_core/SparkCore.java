package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.config.SparkConfig;
import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.js.SparkJS;
import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.registry.client.*;
import cn.solarmoon.spark_core.registry.common.*;
import cn.solarmoon.spark_core.resource.common.MultiModResourceRegistry;
import cn.solarmoon.spark_core.resource2.NativeLoader;
import cn.solarmoon.spark_core.resource2.SparkPackResourceLoader;
import cn.solarmoon.spark_core.web.logging.SparkLogger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
@Mod(SparkCore.MOD_ID)
public class SparkCore {

    public static final String MOD_ID = "spark_core";
    public static final Logger LOGGER = LoggerFactory.getLogger("星火核心");
    public static final SparkLogger ENHANCED_LOGGER = new SparkLogger(LOGGER);
    public static final ObjectRegister REGISTER = new ObjectRegister(MOD_ID, true);
    public static final ObjectRegister MC_REGISTER = new ObjectRegister("minecraft", false);
    public static final MolangParser PARSER = new MolangParser(new HashMap<>(4));

    public SparkCore(IEventBus modEventBus, ModContainer modContainer) {
        // 首先注册SparkCore自身到多mod资源系统
        MultiModResourceRegistry.INSTANCE.registerModResources(MOD_ID, SparkCore.class);
        SparkPackResourceLoader.loadAllModules();
        NativeLoader.load("bullet", PhysicsHelperKt.selectLib());

        REGISTER.register(modEventBus);
        MC_REGISTER.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            SparkClientEventRegister.register();
            SparkModelRegister.register(modEventBus);
            SparkKeyMappings.register();
            SparkShaders.register(modEventBus);
            SparkParticleProviderRegister.register(modEventBus);
            // 注册配置屏幕工厂
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
        SparkJSScriptRegister.register(modEventBus);
        SparkRegistries.register();
        SparkVisualEffects.register();
        SparkAttachments.register();
        SparkCommonEventRegister.register(modEventBus);
        SparkPayloadRegister.register(modEventBus);
        SparkTypedAnimations.register();
        SparkDataComponents.register();
        SparkDataRegistryRegister.register(modEventBus);
        SparkCodeRegister.register(modEventBus);
        SparkCommandRegister.register();
        SyncerTypes.register();
        SparkDataGenerator.register(modEventBus);
        SparkCapabilities.register(modEventBus);
        SparkParticles.register();
        SparkPackModuleRegister.register(modEventBus);
        SparkConfig.register(modContainer);
        SparkStateMachineRegister.register();
        SparkResourceRegister.register(modEventBus);
    }

    public static Logger logger(String suffix) {
        return LoggerFactory.getLogger("星火核心" + "/" + suffix);
    }

}
