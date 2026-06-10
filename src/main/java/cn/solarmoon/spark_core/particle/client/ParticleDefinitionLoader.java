package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 粒子定义加载器。维护一个按 ResourceLocation 索引的定义注册表。
 * 内容包模块 (ParticleModule) 加载完成后调用 reload() 注入定义。
 */
public class ParticleDefinitionLoader {

    private static final ParticleDefinitionLoader INSTANCE = new ParticleDefinitionLoader();

    private final Map<ResourceLocation, ParticleEffectDefinition> definitions = new ConcurrentHashMap<>();

    private ParticleDefinitionLoader() {}

    public static ParticleDefinitionLoader getInstance() {
        return INSTANCE;
    }

    /**
     * 获取粒子定义。
     */
    @Nullable
    public ParticleEffectDefinition getDefinition(ResourceLocation id) {
        return definitions.get(id);
    }

    /**
     * 批量重新加载定义（由内容包模块在 onFinish 时调用）。
     */
    public void reload(Map<ResourceLocation, ParticleEffectDefinition> newDefinitions) {
        definitions.clear();
        definitions.putAll(newDefinitions);
    }

    /**
     * 注册单个定义。
     */
    public void register(ResourceLocation id, ParticleEffectDefinition def) {
        definitions.put(id, def);
    }

    /**
     * 获取所有已加载的定义。
     */
    public Map<ResourceLocation, ParticleEffectDefinition> getAllDefinitions() {
        return definitions;
    }

    /**
     * 清空所有定义。
     */
    public void clear() {
        definitions.clear();
    }
}
