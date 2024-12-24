package cn.solarmoon.spark_core.util;

import cn.solarmoon.spark_core.entry_builder.common.FeatureBuilder;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;

public class RegisterUtil {

    public static void gatherBuilderFix(RegistrySetBuilder builder) {
        builder.add(Registries.CONFIGURED_FEATURE, FeatureBuilder.Gather::configBootStrap);
    }

}
