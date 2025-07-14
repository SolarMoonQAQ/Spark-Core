package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.config.SparkConfig;
import cn.solarmoon.spark_core.js.JSHelperKt;
import cn.solarmoon.spark_core.js.JSApi;
import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.registry.client.SparkKeyMappings;
import cn.solarmoon.spark_core.registry.client.SparkModelRegister;
import cn.solarmoon.spark_core.registry.common.SparkRegistries;
import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.registry.client.SparkClientEventRegister;
import cn.solarmoon.spark_core.registry.common.*;
import cn.solarmoon.spark_core.resource.common.ModResourceInfo;
import cn.solarmoon.spark_core.web.logging.SparkLogger;
import cn.solarmoon.spark_core.resource.common.MultiModResourceRegistry;
import cn.solarmoon.spark_core.rpc.WebSocketRpcServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
@Mod(SparkCore.MOD_ID)
public class SparkCore {

    public static final String MOD_ID = "spark_core";
    public static final Logger LOGGER = LoggerFactory.getLogger("星火核心");
    public static final SparkLogger ENHANCED_LOGGER = new SparkLogger(LOGGER);
    public static final ObjectRegister REGISTER = new ObjectRegister(MOD_ID, true);
    public static final ObjectRegister MC_REGISTER = new ObjectRegister("minecraft", false);
    public static final MolangParser PARSER = new MolangParser(null);

    public SparkCore(IEventBus modEventBus, ModContainer modContainer) {
        // 首先注册SparkCore自身到多mod资源系统
        MultiModResourceRegistry.INSTANCE.registerModResources(MOD_ID, SparkCore.class);

        REGISTER.register(modEventBus);
        MC_REGISTER.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            SparkClientEventRegister.register();
            SparkModelRegister.register(modEventBus);
            SparkKeyMappings.register();
            // 注册配置屏幕工厂
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
        SparkJSApiRegister.register(modEventBus);
        SparkRegistries.register();
        SparkVisualEffects.register();
        SparkAttachments.register();
        SparkCommonEventRegister.register();
        SparkPayloadRegister.register(modEventBus);
        SparkTypedAnimations.register();
        SparkDataComponents.register();
        SparkDataRegistryRegister.register(modEventBus);
        SparkCodeRegister.register(modEventBus);
        SparkCommandRegister.register();
        SyncerTypes.register();
        SparkDataGenerator.register(modEventBus);
        SparkCapabilities.register(modEventBus);
        // 注册配置
        SparkConfig.register(modContainer);
        PhysicsHelperKt.initBullet();
        // 注册资源系统
        SparkResourceRegister.register(modEventBus);
    }
}
