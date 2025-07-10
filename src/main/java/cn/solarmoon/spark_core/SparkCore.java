package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.config.SparkConfig;
import cn.solarmoon.spark_core.js.JSHelperKt;
import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
//import cn.solarmoon.spark_core.physics.sync.PhysicsSyncConfigKt;
import cn.solarmoon.spark_core.registry.client.SparkKeyMappings;
import cn.solarmoon.spark_core.registry.client.SparkModelRegister;
import cn.solarmoon.spark_core.registry.common.SparkRegistries;
import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.registry.client.SparkClientEventRegister;
import cn.solarmoon.spark_core.registry.common.*;
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService;
import cn.solarmoon.spark_core.resource.presets.DynamicResourceApplier;
import cn.solarmoon.spark_core.rpc.WebSocketRpcServer;
// import net.minecraft.core.registries.Registries; // No longer needed here
import net.neoforged.bus.api.IEventBus;
// import net.neoforged.bus.api.SubscribeEvent; // No longer needed here
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
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
        REGISTER.register(modEventBus);
        MC_REGISTER.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            SparkClientEventRegister.register();
            SparkModelRegister.register(modEventBus);
            SparkKeyMappings.register();
//            WebSocketRpcServer rpcServer = new WebSocketRpcServer();
//            rpcServer.start(8080); // 启动服务器
        }

        HandlerDiscoveryService.INSTANCE.discoverAndInitializeHandlers(getClass());
        SparkRegistries.register();
        SparkVisualEffects.register();
        SparkAttachments.register();
        SparkCommonEventRegister.register();
        SparkPayloadRegister.register(modEventBus);
        SparkDataRegister.register();
        SparkTypedAnimations.register();
        SparkDataComponents.register();
        SparkDataRegistryRegister.register(modEventBus);
        SparkCodeRegister.register(modEventBus);
        SparkCommandRegister.register();
        SyncerTypes.register();
        SparkDataGenerator.register(modEventBus);
        SparkCapabilities.register(modEventBus);
        SparkJSApiRegister.register(modEventBus);
        SparkCustomModelItem.register(modEventBus);
        // 注册配置
        SparkConfig.INSTANCE.register(modContainer);
        PhysicsHelperKt.initBullet();
        // JavaScript脚本初始化现在由DynamicJavaScriptHandler的initializeDefaultResources()处理
    }
}
