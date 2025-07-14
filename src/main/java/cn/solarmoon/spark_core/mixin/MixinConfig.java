package cn.solarmoon.spark_core.mixin;

import com.llamalad7.mixinextras.platform.neoforge.MixinExtrasConfigPlugin;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MixinConfig extends MixinExtrasConfigPlugin {

    // 使用独立的Logger避免引用SparkCore触发早期类加载
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkCore-MixinConfig");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 检查是否是CustomNPC相关的mixin
        if (mixinClassName.contains("EntityCustomNpcMixin") || 
            mixinClassName.contains("ModelNPCGolemMixin")) {
            // 在ModList还未初始化时，检查FMLLoader的状态
            try {
                ModList modList = ModList.get();
                if (modList != null) {
                    // 只有在CustomNPC模组存在时才应用这些mixin
                    return modList.isLoaded("customnpcs");
                } else {
                    // ModList未初始化时，默认不应用CustomNPC相关的mixin
                    LOGGER.warn("ModList尚未初始化，跳过CustomNPC相关的mixin: " + mixinClassName);
                    return false;
                }
            } catch (Exception e) {
                // 如果检查失败，默认不应用CustomNPC相关的mixin
                LOGGER.warn("检查CustomNPC模组状态失败，跳过mixin: " + mixinClassName, e);
                return false;
            }
        }
        
        // 其他mixin正常应用
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}
