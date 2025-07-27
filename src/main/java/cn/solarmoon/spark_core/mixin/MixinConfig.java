package cn.solarmoon.spark_core.mixin;

import com.llamalad7.mixinextras.platform.neoforge.MixinExtrasConfigPlugin;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class MixinConfig extends MixinExtrasConfigPlugin {

    // 使用独立的Logger避免引用SparkCore触发早期类加载
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkCore-MixinConfig");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 其他mixin正常应用
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}
