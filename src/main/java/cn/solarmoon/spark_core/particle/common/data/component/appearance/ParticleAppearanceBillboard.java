package cn.solarmoon.spark_core.particle.common.data.component.appearance;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 粒子公告板外观组件。
 * 控制粒子的尺寸、朝向相机模式、UV 和翻页书动画。
 * 对应 JSON key: minecraft:particle_appearance_billboard
 * <p>
 * UV 和 Flipbook 字段当前仅支持静态数值（MoLang 表达式尚未实现，见 §14.5）。
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
            // 基岩版格式: "uv": [32, 88], "uv_size": [8, 8], "texture_width": 128, "texture_height": 128
            float texW = uvObj.has("texture_width") ? uvObj.get("texture_width").getAsFloat() : 1f;
            float texH = uvObj.has("texture_height") ? uvObj.get("texture_height").getAsFloat() : 1f;
            float[] uvArr = uvObj.has("uv") ? readFloatArray(uvObj, "uv", new float[]{0, 0}) : new float[]{0, 0};
            float[] uvSize = uvObj.has("uv_size") ? readFloatArray(uvObj, "uv_size", new float[]{1, 1}) : new float[]{1, 1};
            // 归一化到 [0,1]，像素坐标 / 纹理尺寸
            float u0 = uvArr[0] / texW;
            float v0 = uvArr[1] / texH;
            float u1 = (uvArr[0] + uvSize[0]) / texW;
            float v1 = (uvArr[1] + uvSize[1]) / texH;
            uv = new UV(u0, v0, u1, v1);
        }

        Flipbook flipbook = null;
        if (json.has("flipbook")) {
            JsonObject fb = json.getAsJsonObject("flipbook");
            // 所有数值字段均读为字符串，同时支持数字字面量和 MoLang 表达式
            String[] baseUV = fb.has("base_uv")
                    ? new String[]{fb.get("base_uv").getAsJsonArray().get(0).getAsString(),
                                   fb.get("base_uv").getAsJsonArray().get(1).getAsString()}
                    : new String[]{"0", "0"};
            String[] sizeUV = fb.has("size_uv")
                    ? new String[]{fb.get("size_uv").getAsJsonArray().get(0).getAsString(),
                                   fb.get("size_uv").getAsJsonArray().get(1).getAsString()}
                    : new String[]{"1", "1"};
            String[] stepUV = fb.has("step_uv")
                    ? new String[]{fb.get("step_uv").getAsJsonArray().get(0).getAsString(),
                                   fb.get("step_uv").getAsJsonArray().get(1).getAsString()}
                    : new String[]{"0", "0"};
            String fps = fb.has("fps") ? fb.get("fps").getAsString() : "1";
            String maxFrame = fb.has("max_frame") ? fb.get("max_frame").getAsString() : "0";
            boolean stretch = fb.has("stretch_to_lifetime") && fb.get("stretch_to_lifetime").getAsBoolean();
            boolean loop = fb.has("loop") && fb.get("loop").getAsBoolean();
            flipbook = new Flipbook(baseUV, sizeUV, stepUV, fps, maxFrame, stretch, loop);
        }

        return new ParticleAppearanceBillboard(size, faceCam, uv, flipbook);
    }

    /** 读取 JSON 数组为字符串数组 */
    private static String[] readStringArray(JsonObject json, String key, String[] def) {
        JsonArray arr = json.getAsJsonArray(key);
        if (arr == null || arr.size() < 2) return def;
        return new String[]{arr.get(0).getAsString(), arr.get(1).getAsString()};
    }

    /** 读取 JSON 数组为 float 数组 */
    // TODO: 支持数组元素为 Molang 表达式字符串（如 "math.floor(v.particle_random_2*8)*8"），当前 getAsFloat 会抛 NumberFormatException
    private static float[] readFloatArray(JsonObject json, String key, float[] def) {
        JsonArray arr = json.getAsJsonArray(key);
        if (arr == null || arr.size() < 2) return def;
        return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat()};
    }

    @Override
    public int order() {
        return 100;
    }

    // --- Inner classes ---

    /**
     * UV 坐标定义（归一化到 [0,1]）。
     */
    public static class UV {
        private final float u0, v0, u1, v1;

        public UV(float u0, float v0, float u1, float v1) {
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
        }

        public float getU0() { return u0; }
        public float getV0() { return v0; }
        public float getU1() { return u1; }
        public float getV1() { return v1; }
    }

    /**
     * 翻页书动画定义。
     * <p>
     * 所有数值字段均存储为 MoLang 表达式字符串（对齐 SBM），
     * 运行时编译求值，支持每粒子动态帧率和帧数。
     */
    public static class Flipbook {
        private final String baseU, baseV;
        private final String sizeX, sizeY;
        private final String stepX, stepY;
        private final String fps;
        private final String maxFrame;
        private final boolean stretchToLifetime;
        private final boolean loop;

        public Flipbook(String[] baseUV, String[] sizeUV, String[] stepUV,
                        String fps, String maxFrame,
                        boolean stretchToLifetime, boolean loop) {
            this.baseU = baseUV[0]; this.baseV = baseUV[1];
            this.sizeX = sizeUV[0]; this.sizeY = sizeUV[1];
            this.stepX = stepUV[0]; this.stepY = stepUV[1];
            this.fps = fps; this.maxFrame = maxFrame;
            this.stretchToLifetime = stretchToLifetime; this.loop = loop;
        }

        public String getBaseU() { return baseU; }
        public String getBaseV() { return baseV; }
        public String getSizeX() { return sizeX; }
        public String getSizeY() { return sizeY; }
        public String getStepX() { return stepX; }
        public String getStepY() { return stepY; }
        public String getFps() { return fps; }
        public String getMaxFrame() { return maxFrame; }
        public boolean isStretchToLifetime() { return stretchToLifetime; }
        public boolean isLoop() { return loop; }
    }

    // --- Getter ---

    public String[] getSize() { return size; }
    public String getFaceCameraMode() { return faceCameraMode; }
    public UV getUv() { return uv; }
    public Flipbook getFlipbook() { return flipbook; }
}
