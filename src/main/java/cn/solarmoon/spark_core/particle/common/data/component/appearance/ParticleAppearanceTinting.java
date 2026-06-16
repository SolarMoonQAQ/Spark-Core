package cn.solarmoon.spark_core.particle.common.data.component.appearance;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.*;

/**
 * 粒子着色外观组件。
 * 支持基岩版全部 color 格式：数组 {@code [r,g,b,a]}、十六进制字符串 {@code "#AARRGGBB"}、
 * 对象 {@code {r,g,b,a}}、gradient 渐变。
 * 对应 JSON key: minecraft:particle_appearance_tinting
 */
public class ParticleAppearanceTinting implements IParticleComponentDefinition {

    private final Color color;

    public ParticleAppearanceTinting(Color color) {
        this.color = color;
    }

    /**
     * 从 JSON 对象反序列化。支持 {@code "color"} 字段为以下四种格式：
     * <ol>
     *   <li>数组：{@code [r, g, b, a]} — 每个元素可为数字或 Molang 表达式字符串</li>
     *   <li>十六进制字符串：{@code "#RRGGBB"} 或 {@code "#AARRGGBB"}</li>
     *   <li>对象：{@code {"r": ..., "g": ..., "b": ..., "a": ...}}</li>
     *   <li>渐变对象：{@code {"interpolant": ..., "gradient": {...}}}</li>
     * </ol>
     */
    public static ParticleAppearanceTinting fromJson(JsonObject json) {
        if (!json.has("color")) {
            return new ParticleAppearanceTinting(null);
        }
        JsonElement colorElem = json.get("color");
        Color color = parseColorElement(colorElem);
        return new ParticleAppearanceTinting(color);
    }

    /**
     * 根据 JSON 元素类型分派到对应的颜色解析逻辑。
     */
    private static Color parseColorElement(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return null;
        }
        if (elem.isJsonArray()) {
            return parseColorArray(elem.getAsJsonArray());
        }
        if (elem.isJsonPrimitive()) {
            return parseColorHex(elem.getAsString());
        }
        if (elem.isJsonObject()) {
            return Color.fromJsonObject(elem.getAsJsonObject());
        }
        return null;
    }

    /**
     * 解析数组格式：{@code [r, g, b, a]}。
     * r/g/b 必须有（索引 0/1/2），a 可选（缺失默认 "1"）。
     */
    private static Color parseColorArray(JsonArray arr) {
        if (arr.size() < 3) return null;
        String r = jsonElementToMolangString(arr.get(0), "1");
        String g = jsonElementToMolangString(arr.get(1), "1");
        String b = jsonElementToMolangString(arr.get(2), "1");
        String a = arr.size() >= 4 ? jsonElementToMolangString(arr.get(3), "1") : "1";
        return Color.solid(r, g, b, a);
    }

    /**
     * 解析十六进制字符串：{@code "#RRGGBB"} 或 {@code "#AARRGGBB"}。
     * 返回 solid 模式的 Color，r/g/b/a 各分量用 Molang 字符串表示。
     */
    private static Color parseColorHex(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try {
            String clean = hex.replace("#", "");
            int argb;
            float alpha = 1f;
            if (clean.length() == 6) {
                argb = Integer.parseInt(clean, 16);
            } else if (clean.length() == 8) {
                // 基岩版格式 #AARRGGBB（与标准 ARGB 一致）
                argb = Integer.parseInt(clean.substring(2), 16); // RRGGBB
                alpha = Integer.parseInt(clean.substring(0, 2), 16) / 255f;
            } else {
                return null;
            }
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float b_val = (argb & 0xFF) / 255f;
            return Color.solid(Float.toString(r), Float.toString(g), Float.toString(b_val), Float.toString(alpha));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将 JsonElement 转换为 Molang 表达式字符串。
     * 数字类型取其字符串表示（如 {@code 0.5} → {@code "0.5"}），
     * 字符串类型直接使用原值。
     */
    private static String jsonElementToMolangString(JsonElement elem, String def) {
        if (elem == null || elem.isJsonNull()) return def;
        if (elem.isJsonPrimitive()) {
            JsonPrimitive prim = elem.getAsJsonPrimitive();
            if (prim.isString()) return prim.getAsString();
            if (prim.isNumber()) return prim.getAsString(); // 数字 → 字符串，如 0.5 → "0.5"
        }
        return def;
    }

    @Override
    public int order() {
        return 200;
    }

    // --- Color ---

    /**
     * 粒子颜色定义。
     * 支持 solid / gradient / ramp_gradient 三种模式。
     * <p>
     * solid 模式的 r/g/b/a 字段存储 Molang 表达式字符串（可为纯数字如 "0.5" 或表达式如 "v.particle_random_1"）。
     */
    public static class Color {
        private final String mode; // "solid", "gradient", "ramp_gradient"
        private final String r, g, b, a;
        private final String interpolant;
        private final List<ColorStop> gradient;

        private Color(String mode, String r, String g, String b, String a, String interpolant, List<ColorStop> gradient) {
            this.mode = mode;
            this.r = r; this.g = g; this.b = b; this.a = a;
            this.interpolant = interpolant;
            this.gradient = gradient;
        }

        public static Color solid(String r, String g, String b, String a) {
            return new Color("solid", r, g, b, a, null, null);
        }

        public static Color gradient(String interpolant, List<ColorStop> gradient) {
            return new Color("gradient", null, null, null, null, interpolant, gradient);
        }

        public static Color rampGradient(String interpolant, List<ColorStop> gradient) {
            return new Color("ramp_gradient", null, null, null, null, interpolant, gradient);
        }

        /**
         * 从 JSON 对象反序列化颜色。支持两种子格式：
         * <ol>
         *   <li>solid 对象：{@code {"r": ..., "g": ..., "b": ..., "a": ...}}</li>
         *   <li>渐变对象：{@code {"interpolant": ..., "gradient": {...}}}
         *       其中 gradient 可为对象（key=停靠点，value=颜色）或数组（均匀分布）</li>
         * </ol>
         */
        static Color fromJsonObject(JsonObject json) {
            // 渐变模式：有 interpolant 字段
            String interpolant = json.has("interpolant") ? json.get("interpolant").getAsString() : null;

            // gradient 子对象
            String gradientMode = null;
            List<ColorStop> stops = null;
            if (json.has("gradient")) {
                gradientMode = "gradient";
                stops = parseGradientStops(json.get("gradient"));
            } else if (json.has("ramp_gradient")) {
                gradientMode = "ramp_gradient";
                stops = parseGradientStops(json.get("ramp_gradient"));
            }

            if (gradientMode != null && stops != null && !stops.isEmpty()) {
                if ("ramp_gradient".equals(gradientMode)) {
                    return Color.rampGradient(interpolant != null ? interpolant : "0", stops);
                }
                return Color.gradient(interpolant != null ? interpolant : "0", stops);
            }

            // solid 模式
            String r = json.has("r") ? json.get("r").getAsString() : "1";
            String g = json.has("g") ? json.get("g").getAsString() : "1";
            String b = json.has("b") ? json.get("b").getAsString() : "1";
            String a = json.has("a") ? json.get("a").getAsString() : "1";
            return Color.solid(r, g, b, a);
        }

        /**
         * 解析 gradient/ramp_gradient 中的色标列表。
         * 支持两种 Bedrock 格式：
         * <ul>
         *   <li>对象格式：{@code {"0.0": "#FF0000FF", "0.5": "#00FF00FF"}} — key 为停靠点位置</li>
         *   <li>数组格式：{@code ["#FF0000FF", "#00FF00FF"]} — 均匀分布停靠点</li>
         * </ul>
         */
        private static List<ColorStop> parseGradientStops(JsonElement gradElem) {
            if (gradElem == null) return Collections.emptyList();

            if (gradElem.isJsonObject()) {
                // 对象格式：key=位置, value=颜色（可为十六进制字符串或数组 [r,g,b,a]）
                JsonObject obj = gradElem.getAsJsonObject();
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<>();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    entries.add(entry);
                }
                // 按键数值排序
                entries.sort(Comparator.comparingDouble(e -> {
                    try {
                        return Double.parseDouble(e.getKey());
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                }));

                List<ColorStop> stops = new ArrayList<>();
                for (Map.Entry<String, JsonElement> entry : entries) {
                    float pos;
                    try {
                        pos = Float.parseFloat(entry.getKey());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    String colorStr = parseColorField(entry.getValue());
                    if (colorStr != null) {
                        stops.add(new ColorStop(pos, colorStr));
                    }
                }
                return stops;
            }

            if (gradElem.isJsonArray()) {
                // 数组格式：均匀分布
                JsonArray arr = gradElem.getAsJsonArray();
                if (arr.isEmpty()) return Collections.emptyList();
                List<ColorStop> stops = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    float pos = arr.size() == 1 ? 0f : (float) i / (arr.size() - 1);
                    String colorStr = parseColorField(arr.get(i));
                    if (colorStr != null) {
                        stops.add(new ColorStop(pos, colorStr));
                    }
                }
                return stops;
            }

            return Collections.emptyList();
        }

        /**
         * 解析渐变中的单个颜色值。支持：
         * <ul>
         *   <li>十六进制字符串（如 {@code "#FF00FFCC"}）</li>
         *   <li>RGBA 数组（如 {@code [1, 0, 1, 0.8]}）</li>
         * </ul>
         * 返回十六进制字符串表示，供运行时 {@code parseHexColor} 使用。
         */
        private static String parseColorField(JsonElement elem) {
            if (elem == null) return null;
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                return elem.getAsString(); // 十六进制字符串
            }
            if (elem.isJsonArray()) {
                JsonArray arr = elem.getAsJsonArray();
                if (arr.size() >= 3) {
                    // 将 [r, g, b, a] 转为十六进制
                    int ri = Math.round(jsonElementToFloat(arr.get(0), 1f) * 255);
                    int gi = Math.round(jsonElementToFloat(arr.get(1), 1f) * 255);
                    int bi = Math.round(jsonElementToFloat(arr.get(2), 1f) * 255);
                    int ai = arr.size() >= 4 ? Math.round(jsonElementToFloat(arr.get(3), 1f) * 255) : 255;
                    // 基岩版标准 #AARRGGBB 格式（Alpha 在前）
                    return String.format("#%02X%02X%02X%02X", ai, ri, gi, bi);
                }
            }
            return "#FFFFFFFF"; // 默认白色
        }

        private static float jsonElementToFloat(JsonElement elem, float def) {
            if (elem == null || elem.isJsonNull()) return def;
            if (elem.isJsonPrimitive()) {
                JsonPrimitive prim = elem.getAsJsonPrimitive();
                if (prim.isNumber()) return prim.getAsFloat();
                if (prim.isString()) {
                    try { return Float.parseFloat(prim.getAsString()); }
                    catch (NumberFormatException ignored) {}
                }
            }
            return def;
        }

        public String getMode() { return mode; }
        public String getR() { return r; }
        public String getG() { return g; }
        public String getB() { return b; }
        public String getA() { return a; }
        public String getInterpolant() { return interpolant; }
        public List<ColorStop> getGradient() { return gradient; }
    }

    /**
     * 渐变色标。
     */
    public static class ColorStop {
        private final float position;
        private final String color; // 十六进制字符串如 "#FF00FFCC"

        public ColorStop(float position, String color) {
            this.position = position;
            this.color = color;
        }

        public float getPosition() { return position; }
        public String getColor() { return color; }
    }

    public Color getColor() { return color; }
}
