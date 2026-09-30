package cn.solarmoon.spark_core.particle.common.data.component.appearance;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 粒子公告板外观组件。
 * 控制粒子的尺寸、朝向相机模式、UV 和翻页书动画。
 * 对应 JSON key: minecraft:particle_appearance_billboard
 * <p>
 * UV 与翻页书字段在规范里都允许 MoLang 表达式。**逐粒子求值尚未实现**
 * （见 particle_soa_plan.md §14.5）：无法解析为数值的元素回退到默认值并告警一次，
 * 而不是抛异常让整个粒子定义加载失败（后者在游戏内表现为该效果整体缺失）。
 */
public class ParticleAppearanceBillboard implements IParticleComponentDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleAppearanceBillboard.class);

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
        // 规范字段名是 facing_camera_mode；face_camera_mode 是早期拼写，保留兼容
        String faceCam = json.has("facing_camera_mode") ? json.get("facing_camera_mode").getAsString()
                : json.has("face_camera_mode") ? json.get("face_camera_mode").getAsString()
                : "rotate_xyz";

        UV uv = null;
        Flipbook flipbook = null;
        if (json.has("uv")) {
            JsonObject uvObj = json.getAsJsonObject("uv");
            // 基岩版格式: "uv": [32, 88], "uv_size": [8, 8], "texture_width": 128, "texture_height": 128
            float texW = uvObj.has("texture_width") ? uvObj.get("texture_width").getAsFloat() : 1f;
            float texH = uvObj.has("texture_height") ? uvObj.get("texture_height").getAsFloat() : 1f;
            float[] uvArr = readFloatArray(uvObj, "uv", new float[]{0, 0});
            float[] uvSize = readFloatArray(uvObj, "uv_size", new float[]{1, 1});
            // 归一化到 [0,1]，像素坐标 / 纹理尺寸
            float u0 = uvArr[0] / texW;
            float v0 = uvArr[1] / texH;
            float u1 = (uvArr[0] + uvSize[0]) / texW;
            float v1 = (uvArr[1] + uvSize[1]) / texH;
            uv = new UV(u0, v0, u1, v1);

            if (uvObj.has("flipbook")) {
                JsonObject fb = uvObj.getAsJsonObject("flipbook");
                // 规范字段名是大写 UV（base_UV/size_UV/step_UV），小写是早期拼写，两者都接受；
                // 与上面的 uv/uv_size 同为**像素**坐标，运行时按 texture_width/height 归一化。
                String[] baseUV = readStringPair(fb, "base_UV", "base_uv", new String[]{"0", "0"});
                String[] sizeUV = readStringPair(fb, "size_UV", "size_uv", new String[]{"1", "1"});
                String[] stepUV = readStringPair(fb, "step_UV", "step_uv", new String[]{"0", "0"});
                String fps = fb.has("fps") ? fb.get("fps").getAsString() : "1";
                String maxFrame = fb.has("max_frame") ? fb.get("max_frame").getAsString() : "0";
                boolean stretch = fb.has("stretch_to_lifetime") && fb.get("stretch_to_lifetime").getAsBoolean();
                boolean loop = fb.has("loop") && fb.get("loop").getAsBoolean();
                flipbook = new Flipbook(baseUV, sizeUV, stepUV, fps, maxFrame, stretch, loop, texW, texH);
            }
        }

        return new ParticleAppearanceBillboard(size, faceCam, uv, flipbook);
    }

    /** 读取 JSON 数组为字符串数组；值不是数组或长度不足时返回默认值 */
    private static String[] readStringArray(JsonObject json, String key, String[] def) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) return def;
        JsonArray arr = element.getAsJsonArray();
        if (arr.size() < 2) return def;
        return new String[]{arr.get(0).getAsString(), arr.get(1).getAsString()};
    }

    /** 读取二元素字符串数组，规范键名与旧键名都试；都不是数组时返回默认值 */
    private static String[] readStringPair(JsonObject json, String specKey, String legacyKey, String[] def) {
        JsonElement element = json.has(specKey) ? json.get(specKey)
                : json.has(legacyKey) ? json.get(legacyKey) : null;
        if (element == null || !element.isJsonArray()) return def;
        JsonArray arr = element.getAsJsonArray();
        if (arr.size() < 2) return def;
        return new String[]{arr.get(0).getAsString(), arr.get(1).getAsString()};
    }

    /**
     * 读取 JSON 数组为 float 数组。元素可以是数字、数字字符串，也可以是 MoLang 表达式字符串
     * （规范允许，如 {@code "math.floor(variable.particle_random_2*8)*8"}）。
     * 表达式求值尚未实现（§14.5），因此**解析不出数值的元素回退到该位置上的默认值并告警一次**：
     * 对 {@code uv} 而言默认值是 0（即该行的第一格精灵），对 {@code uv_size} 是 1。
     */
    private static float[] readFloatArray(JsonObject json, String key, float[] def) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) return def;
        JsonArray arr = element.getAsJsonArray();
        if (arr.size() < 2) return def;

        float[] out = new float[]{def[0], def[1]};
        boolean warned = false;
        for (int i = 0; i < 2; i++) {
            Float value = asFloat(arr.get(i));
            if (value != null) {
                out[i] = value;
            } else if (!warned) {
                warned = true;
                LOGGER.warn("[particle_appearance_billboard.{}] 第 {} 个元素不是数值（MoLang 表达式求值尚未实现，"
                        + "见 particle_soa_plan.md §14.5），已回退默认值 {}", key, i, def[i]);
            }
        }
        return out;
    }

    /** 元素 → float：数字直接取，字符串尝试解析，其余（含 MoLang 表达式）返回 null */
    @Nullable
    private static Float asFloat(@Nullable JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        if (element.getAsJsonPrimitive().isNumber()) return element.getAsFloat();
        try {
            return Float.parseFloat(element.getAsString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
     * 所有数值字段均存储为 MoLang 表达式字符串（对齐 SBM），运行时编译求值，支持每粒子动态帧率和帧数。
     * UV 三元组为**像素**坐标，与同组件的 {@code uv}/{@code uv_size} 一致，运行时按纹理尺寸归一化。
     */
    public static class Flipbook {
        private final String baseU, baseV;
        private final String sizeX, sizeY;
        private final String stepX, stepY;
        private final String fps;
        private final String maxFrame;
        private final boolean stretchToLifetime;
        private final boolean loop;
        private final float textureWidth;
        private final float textureHeight;

        public Flipbook(String[] baseUV, String[] sizeUV, String[] stepUV,
                        String fps, String maxFrame,
                        boolean stretchToLifetime, boolean loop,
                        float textureWidth, float textureHeight) {
            this.baseU = baseUV[0]; this.baseV = baseUV[1];
            this.sizeX = sizeUV[0]; this.sizeY = sizeUV[1];
            this.stepX = stepUV[0]; this.stepY = stepUV[1];
            this.fps = fps; this.maxFrame = maxFrame;
            this.stretchToLifetime = stretchToLifetime; this.loop = loop;
            this.textureWidth = textureWidth; this.textureHeight = textureHeight;
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
        public float getTextureWidth() { return textureWidth; }
        public float getTextureHeight() { return textureHeight; }
    }

    // --- Getter ---

    public String[] getSize() { return size; }
    public String getFaceCameraMode() { return faceCameraMode; }
    public UV getUv() { return uv; }
    public Flipbook getFlipbook() { return flipbook; }
}
