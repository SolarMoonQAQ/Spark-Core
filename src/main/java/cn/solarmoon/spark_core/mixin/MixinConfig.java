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
        // Create 兼容 Mixin 仅在 Create 类存在时启用，确保可选兼容不触发类加载崩溃。
        if (mixinClassName.contains(".compat.create.")) {
            return hasClass("com.simibubi.create.content.contraptions.Contraption");
        }
        // 其他mixin正常应用
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }

    /**
     * 检测指定类在当前运行环境是否可加载。
     */
    private boolean hasClass(String className) {
        try {
            Class.forName(className, false, this.getClass().getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
