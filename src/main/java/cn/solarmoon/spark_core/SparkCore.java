package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.registry.client.SparkModelRegister;
import cn.solarmoon.spark_core.registry.common.SparkRegistries;
import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.registry.client.SparkClientEventRegister;
import cn.solarmoon.spark_core.registry.common.*;
import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
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
        SparkTypedAnimations.register();
        SparkDataComponents.register();

        initPhysics();
    }

    private void initPhysics() {
        try {
            LibraryInfo info = new LibraryInfo(
                    new DirectoryPath("linux/x86-64/com/github/stephengold"),
                    "bulletjme", DirectoryPath.USER_DIR);
            NativeBinaryLoader loader = new NativeBinaryLoader(info);
            NativeDynamicLibrary[] libraries = new NativeDynamicLibrary[]{
                    new NativeDynamicLibrary("native/linux/arm64", PlatformPredicate.LINUX_ARM_64),
                    new NativeDynamicLibrary("native/linux/arm32", PlatformPredicate.LINUX_ARM_32),
                    new NativeDynamicLibrary("native/linux/x86_64", PlatformPredicate.LINUX_X86_64),
                    new NativeDynamicLibrary("native/osx/arm64", PlatformPredicate.MACOS_ARM_64),
                    new NativeDynamicLibrary("native/osx/x86_64", PlatformPredicate.MACOS_X86_64),
                    new NativeDynamicLibrary("native/windows/x86_64", PlatformPredicate.WIN_X86_64)
            };
            loader.registerNativeLibraries(libraries).initPlatformLibrary();
            loader.loadLibrary(LoadingCriterion.INCREMENTAL_LOADING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
