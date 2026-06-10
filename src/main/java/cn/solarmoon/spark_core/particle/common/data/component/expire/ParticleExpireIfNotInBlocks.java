package cn.solarmoon.spark_core.particle.common.data.component.expire;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.*;

/**
 * 粒子不在方块内过期组件。当粒子不在指定方块 ID 列表中的方块内时过期。
 * 对应 JSON key: minecraft:particle_expire_if_not_in_blocks
 * <p>
 * 对标 SBM/Bedrock 格式：裸数组 {@code ["minecraft:air"]}
 */
public class ParticleExpireIfNotInBlocks implements IParticleComponentDefinition {

    private final Set<String> blocks;

    public ParticleExpireIfNotInBlocks(Set<String> blocks) {
        this.blocks = blocks;
    }

    /**
     * 从裸 JSON 数组反序列化。
     */
    public static ParticleExpireIfNotInBlocks fromJson(JsonElement value) {
        Set<String> blocks = new LinkedHashSet<>();
        if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            for (JsonElement el : arr) {
                blocks.add(el.getAsString());
            }
        }
        return new ParticleExpireIfNotInBlocks(blocks);
    }

    @Override
    public int order() {
        return 340;
    }

    public Set<String> getBlocks() { return blocks; }
}
