package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.js.JSHelperKt;
import cn.solarmoon.spark_core.js.SparkJS;
import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.registry.client.SparkModelRegister;
import cn.solarmoon.spark_core.registry.common.SparkRegistries;
import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.registry.client.SparkClientEventRegister;
import cn.solarmoon.spark_core.registry.common.*;
import cn.solarmoon.spark_core.rpc.WebSocketRpcServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Mod(SparkCore.MOD_ID)
public class SparkCore {

    public static final String MOD_ID = "spark_core";
    public static final Logger LOGGER = LoggerFactory.getLogger("星火核心");
    public static final ObjectRegister REGISTER = new ObjectRegister(MOD_ID, true);
    public static final ObjectRegister MC_REGISTER = new ObjectRegister("minecraft", false);
    public static final MolangParser PARSER = new MolangParser(null);

    public SparkCore(IEventBus modEventBus, ModContainer modContainer) {
        REGISTER.register(modEventBus);
        MC_REGISTER.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            SparkClientEventRegister.register();
            SparkModelRegister.register(modEventBus);
            // 启动 WebSocket 服务器
            WebSocketRpcServer rpcServer = new WebSocketRpcServer();
            rpcServer.start(8080); // 启动服务器
        }

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

        PhysicsHelperKt.initBullet();
        JSHelperKt.loadDefaultScripts(getClass());
    }

}
