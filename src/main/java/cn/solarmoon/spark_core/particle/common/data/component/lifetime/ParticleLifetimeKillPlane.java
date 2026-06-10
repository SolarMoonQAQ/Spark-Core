package cn.solarmoon.spark_core.particle.common.data.component.lifetime;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;

/**
 * 粒子生命周期杀戮平面组件。
 * 定义三个平面的系数，粒子穿过任一平面时过期。
 * 对应 JSON key: minecraft:particle_lifetime_kill_plane
 */
public class ParticleLifetimeKillPlane implements IParticleComponentDefinition {

    private final float[] planeX;
    private final float[] planeY;
    private final float[] planeZ;

    public ParticleLifetimeKillPlane(float[] planeX, float[] planeY, float[] planeZ) {
        this.planeX = planeX;
        this.planeY = planeY;
        this.planeZ = planeZ;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleLifetimeKillPlane fromJson(JsonObject json) {
        float[] px = new float[]{0, 0, 0, 0};
        float[] py = new float[]{0, 0, 0, 0};
        float[] pz = new float[]{0, 0, 0, 0};

        if (json.has("kill_plane")) {
            JsonObject kp = json.getAsJsonObject("kill_plane");
            if (kp.has("x")) {
                JsonArray arr = kp.getAsJsonArray("x");
                for (int i = 0; i < Math.min(arr.size(), 4); i++) {
                    px[i] = arr.get(i).getAsFloat();
                }
            }
            if (kp.has("y")) {
                JsonArray arr = kp.getAsJsonArray("y");
                for (int i = 0; i < Math.min(arr.size(), 4); i++) {
                    py[i] = arr.get(i).getAsFloat();
                }
            }
            if (kp.has("z")) {
                JsonArray arr = kp.getAsJsonArray("z");
                for (int i = 0; i < Math.min(arr.size(), 4); i++) {
                    pz[i] = arr.get(i).getAsFloat();
                }
            }
        }

        return new ParticleLifetimeKillPlane(px, py, pz);
    }

    @Override
    public int order() {
        return 320;
    }

    public float[] getPlaneX() { return planeX; }
    public float[] getPlaneY() { return planeY; }
    public float[] getPlaneZ() { return planeZ; }
}
