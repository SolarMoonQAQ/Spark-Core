package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.registry.client.SparkModelRegister;
import cn.solarmoon.spark_core.registry.common.SparkRegistries;
import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.registry.client.SparkClientEventRegister;
import cn.solarmoon.spark_core.registry.common.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.ode4j.ode.OdeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SparkCore.MOD_ID)
public class SparkCore {

    public static final String MOD_ID = "spark_core";
    public static final Logger LOGGER = LoggerFactory.getLogger("星火核心");
    public static final ObjectRegister REGISTER = new ObjectRegister(MOD_ID, true);

    public SparkCore(IEventBus modEventBus, ModContainer modContainer) {
        REGISTER.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            SparkClientEventRegister.register();
            SparkModelRegister.register(modEventBus);
        }

        SparkRegistries.register();

        SparkVisualEffects.register();
        SparkAttachments.register();
        SparkCommonEventRegister.register();
        SparkPayloadRegister.register(modEventBus);
        SparkDataRegister.register();
        SparkPhysLevelRegister.register();
        SparkTypedAnimations.register();
        SparkDataComponents.register();
        SparkBodyTypes.register();

        OdeHelper.initODE();
    }

}
