package cn.solarmoon.spark_core.particle.common.data.component.expire;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.*;

/**
 * 粒子在方块内过期组件。当粒子位于指定方块 ID 列表中的方块内时过期。
 * 对应 JSON key: minecraft:particle_expire_if_in_blocks
 * <p>
 * 对标 SBM/Bedrock 格式：裸数组 {@code ["minecraft:water", "minecraft:lava"]}
 */
public class ParticleExpireIfInBlocks implements IParticleComponentDefinition {

    private final Set<String> blocks;

    public ParticleExpireIfInBlocks(Set<String> blocks) {
        this.blocks = blocks;
    }

    /**
     * 从裸 JSON 数组反序列化。
     */
    public static ParticleExpireIfInBlocks fromJson(JsonElement value) {
        Set<String> blocks = new LinkedHashSet<>();
        if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            for (JsonElement el : arr) {
                blocks.add(el.getAsString());
            }
        }
        return new ParticleExpireIfInBlocks(blocks);
    }

    @Override
    public int order() {
        return 330;
    }

    public Set<String> getBlocks() { return blocks; }
}
