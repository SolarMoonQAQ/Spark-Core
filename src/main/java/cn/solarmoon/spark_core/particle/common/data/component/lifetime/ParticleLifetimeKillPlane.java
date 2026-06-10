package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * 粒子生命周期杀戮平面组件。对应 JSON key: minecraft:particle_lifetime_kill_plane
 * <p>
 * 对标 SBM/Bedrock 格式：裸数组 {@code [a, b, c, d]}（4 个系数定义一个平面 ax+by+cz+d=0），
 * 可重复多个平面（长度 8/12/...）。
 */
public class ParticleLifetimeKillPlane implements IParticleComponentDefinition {

    /** 平面列表，每组 4 个 float 定义 ax+by+cz+d=0 */
    private final List<float[]> planes;

    public ParticleLifetimeKillPlane(List<float[]> planes) {
        this.planes = planes;
    }

    /**
     * 从裸 JSON 数组反序列化。长度必须为 4 的倍数。
     */
    public static ParticleLifetimeKillPlane fromJson(JsonElement value) {
        if (!value.isJsonArray()) {
            throw new JsonParseException("minecraft:particle_lifetime_kill_plane 必须是 JSON 数组 [a,b,c,d, ...]");
        }
        JsonArray arr = value.getAsJsonArray();
        if (arr.size() % 4 != 0) {
            throw new JsonParseException("minecraft:particle_lifetime_kill_plane 数组长度必须为 4 的倍数，实际: " + arr.size());
        }

        List<float[]> planes = new ArrayList<>();
        for (int i = 0; i < arr.size(); i += 4) {
            float[] plane = new float[]{
                    arr.get(i).getAsFloat(),
                    arr.get(i + 1).getAsFloat(),
                    arr.get(i + 2).getAsFloat(),
                    arr.get(i + 3).getAsFloat()
            };
            planes.add(plane);
        }
        return new ParticleLifetimeKillPlane(planes);
    }

    @Override
    public int order() {
        return 320;
    }

    public List<float[]> getPlanes() { return planes; }
}
