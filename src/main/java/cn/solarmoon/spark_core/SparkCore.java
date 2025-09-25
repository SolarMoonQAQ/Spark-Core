package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.registry.client.*;
import cn.solarmoon.spark_core.registry.common.*;
import cn.solarmoon.spark_core.pack.NativeLoader;
import cn.solarmoon.spark_core.pack.SparkPackResourceLoader;
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
    public static final ObjectRegister REGISTER = new ObjectRegister(MOD_ID, true);
    public static final ObjectRegister MC_REGISTER = new ObjectRegister("minecraft", false);
    public static final MolangParser PARSER = new MolangParser(new HashMap<>(4));

    public SparkCore(IEventBus modEventBus, ModContainer modContainer) {
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
        SparkSyncerTypes.register();
        SparkDataGenerator.register(modEventBus);
        SparkCapabilities.register();
        SparkParticles.register();
        SparkPackModuleRegister.register(modEventBus);
        SparkStateMachineRegister.register();
        SparkDiffSyncSchemas.register();
        SparkCollisionObjectTypes.register();
        SparkCollisionShapeTypes.register();
    }

    public static Logger logger(String suffix) {
        return LoggerFactory.getLogger("星火核心" + "/" + suffix);
    }

}
