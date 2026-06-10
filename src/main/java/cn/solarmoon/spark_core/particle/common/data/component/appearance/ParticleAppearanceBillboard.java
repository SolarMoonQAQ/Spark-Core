package cn.solarmoon.spark_core.particle.common.data.component.appearance;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 粒子公告板外观组件。
 * 控制粒子的尺寸、朝向相机模式、UV 和翻页书动画。
 * 对应 JSON key: minecraft:particle_appearance_billboard
 */
public class ParticleAppearanceBillboard implements IParticleComponentDefinition {

    private final String[] size;
    private final String faceCameraMode;
    private final UV uv;
    private final Flipbook flipbook;

    public ParticleAppearanceBillboard(String[] size, String faceCameraMode, UV uv, Flipbook flipbook) {
        this.size = size;
        this.faceCameraMode = faceCameraMode;
        this.uv = uv;
        this.flipbook = flipbook;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleAppearanceBillboard fromJson(JsonObject json) {
        String[] size = readStringArray(json, "size", new String[]{"0.25", "0.25"});
        String faceCam = json.has("face_camera_mode") ? json.get("face_camera_mode").getAsString() : "rotate_xyz";

        UV uv = null;
        if (json.has("uv")) {
            JsonObject uvObj = json.getAsJsonObject("uv");
            float u0 = uvObj.has("u0") ? uvObj.get("u0").getAsFloat() : 0f;
            float v0 = uvObj.has("v0") ? uvObj.get("v0").getAsFloat() : 0f;
            float u1 = uvObj.has("u1") ? uvObj.get("u1").getAsFloat() : 1f;
            float v1 = uvObj.has("v1") ? uvObj.get("v1").getAsFloat() : 1f;
            Float texW = uvObj.has("texture_size") ? uvObj.getAsJsonArray("texture_size").get(0).getAsFloat() : null;
            Float texH = uvObj.has("texture_size") ? uvObj.getAsJsonArray("texture_size").get(1).getAsFloat() : null;
            uv = new UV(u0, v0, u1, v1, texW, texH);
        }

        Flipbook flipbook = null;
        if (json.has("flipbook")) {
            JsonObject fb = json.getAsJsonObject("flipbook");
            float baseU = fb.has("base_uv") ? fb.getAsJsonArray("base_uv").get(0).getAsFloat() : 0f;
            float baseV = fb.has("base_uv") ? fb.getAsJsonArray("base_uv").get(1).getAsFloat() : 0f;
            float sizeX = fb.has("size_uv") ? fb.getAsJsonArray("size_uv").get(0).getAsFloat() : 1f;
            float sizeY = fb.has("size_uv") ? fb.getAsJsonArray("size_uv").get(1).getAsFloat() : 1f;
            float stepX = fb.has("step_uv") ? fb.getAsJsonArray("step_uv").get(0).getAsFloat() : 0f;
            float stepY = fb.has("step_uv") ? fb.getAsJsonArray("step_uv").get(1).getAsFloat() : 0f;
            int fps = fb.has("fps") ? fb.get("fps").getAsInt() : 1;
            int maxFrame = fb.has("max_frame") ? fb.get("max_frame").getAsInt() : 0;
            boolean stretch = fb.has("stretch_to_lifetime") && fb.get("stretch_to_lifetime").getAsBoolean();
            boolean loop = fb.has("loop") && fb.get("loop").getAsBoolean();
            flipbook = new Flipbook(baseU, baseV, sizeX, sizeY, stepX, stepY, fps, maxFrame, stretch, loop);
        }

        return new ParticleAppearanceBillboard(size, faceCam, uv, flipbook);
    }

    private static String[] readStringArray(JsonObject json, String key, String[] def) {
        JsonArray arr = json.getAsJsonArray(key);
        if (arr == null || arr.size() < 2) return def;
        return new String[]{arr.get(0).getAsString(), arr.get(1).getAsString()};
    }

    @Override
    public int order() {
        return 100;
    }

    // --- Inner classes ---

    /**
     * UV 坐标定义。
     */
    public static class UV {
        private final float u0, v0, u1, v1;
        private final Float textureSizeX;
        private final Float textureSizeY;

        public UV(float u0, float v0, float u1, float v1, Float textureSizeX, Float textureSizeY) {
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
            this.textureSizeX = textureSizeX; this.textureSizeY = textureSizeY;
        }

        public float getU0() { return u0; }
        public float getV0() { return v0; }
        public float getU1() { return u1; }
        public float getV1() { return v1; }
        public Float getTextureSizeX() { return textureSizeX; }
        public Float getTextureSizeY() { return textureSizeY; }
    }

    /**
     * 翻页书动画定义。
     */
    public static class Flipbook {
        private final float baseU, baseV;
        private final float sizeX, sizeY;
        private final float stepX, stepY;
        private final int fps;
        private final int maxFrame;
        private final boolean stretchToLifetime;
        private final boolean loop;

        public Flipbook(float baseU, float baseV, float sizeX, float sizeY,
                         float stepX, float stepY, int fps, int maxFrame,
                         boolean stretchToLifetime, boolean loop) {
            this.baseU = baseU; this.baseV = baseV;
            this.sizeX = sizeX; this.sizeY = sizeY;
            this.stepX = stepX; this.stepY = stepY;
            this.fps = fps; this.maxFrame = maxFrame;
            this.stretchToLifetime = stretchToLifetime; this.loop = loop;
        }

        public float getBaseU() { return baseU; }
        public float getBaseV() { return baseV; }
        public float getSizeX() { return sizeX; }
        public float getSizeY() { return sizeY; }
        public float getStepX() { return stepX; }
        public float getStepY() { return stepY; }
        public int getFps() { return fps; }
        public int getMaxFrame() { return maxFrame; }
        public boolean isStretchToLifetime() { return stretchToLifetime; }
        public boolean isLoop() { return loop; }
    }

    // --- Getter ---

    public String[] getSize() { return size; }
    public String getFaceCameraMode() { return faceCameraMode; }
    public UV getUv() { return uv; }
    public Flipbook getFlipbook() { return flipbook; }
}
