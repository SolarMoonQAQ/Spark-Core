package cn.solarmoon.spark_core.particle.common.data.component.expire;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 粒子在方块内过期组件。
 * 当粒子位于指定方块 ID 列表中的方块内时，粒子过期。
 * 对应 JSON key: minecraft:particle_expire_if_in_blocks
 */
public class ParticleExpireIfInBlocks implements IParticleComponentDefinition {

    private final List<String> blocks;

    public ParticleExpireIfInBlocks(List<String> blocks) {
        this.blocks = blocks;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleExpireIfInBlocks fromJson(JsonObject json) {
        List<String> blocks = new ArrayList<>();
        if (json.has("blocks")) {
            JsonArray arr = json.getAsJsonArray("blocks");
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

    public List<String> getBlocks() { return Collections.unmodifiableList(blocks); }
}
