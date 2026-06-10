package cn.solarmoon.spark_core.particle.common.data.component.appearance;

import cn.solarmoon.spark_core.particle.common.data.IParticleComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 粒子着色外观组件。
 * 支持三种着色模式：纯色（solid）、线性渐变（gradient）、斜坡渐变（ramp_gradient）。
 * 对应 JSON key: minecraft:particle_appearance_tinting
 */
public class ParticleAppearanceTinting implements IParticleComponentDefinition {

    private final Color color;

    public ParticleAppearanceTinting(Color color) {
        this.color = color;
    }

    /**
     * 从 JSON 对象反序列化。
     */
    public static ParticleAppearanceTinting fromJson(JsonObject json) {
        if (!json.has("color")) {
            return new ParticleAppearanceTinting(null);
        }
        JsonObject colorObj = json.getAsJsonObject("color");
        Color color = Color.fromJson(colorObj);
        return new ParticleAppearanceTinting(color);
    }

    @Override
    public int order() {
        return 200;
    }

    // --- Color ---

    /**
     * 粒子颜色定义。
     * 支持 solid / gradient / ramp_gradient 三种模式。
     */
    public static class Color {
        private final String mode; // "solid", "gradient", "ramp_gradient"
        // solid 模式：rgba 四个分量 (Molang 表达式)
        private final String r, g, b, a;
        // gradient / ramp_gradient 模式：渐变色标列表
        private final String interpolant;
        private final List<ColorStop> gradient;

        private Color(String mode, String r, String g, String b, String a, String interpolant, List<ColorStop> gradient) {
            this.mode = mode;
            this.r = r; this.g = g; this.b = b; this.a = a;
            this.interpolant = interpolant;
            this.gradient = gradient;
        }

        /**
         * 创建纯色颜色。
         */
        public static Color solid(String r, String g, String b, String a) {
            return new Color("solid", r, g, b, a, null, null);
        }

        /**
         * 创建渐变色。
         */
        public static Color gradient(String interpolant, List<ColorStop> gradient) {
            return new Color("gradient", null, null, null, null, interpolant, gradient);
        }

        /**
         * 创建斜坡渐变色。
         */
        public static Color rampGradient(String interpolant, List<ColorStop> gradient) {
            return new Color("ramp_gradient", null, null, null, null, interpolant, gradient);
        }

        /**
         * 从 JSON 对象反序列化颜色。
         */
        public static Color fromJson(JsonObject json) {
            // 判断模式：有 "gradient" 数组为渐变模式
            if (json.has("gradient")) {
                JsonObject gradObj = json.getAsJsonObject("gradient");
                String interpolant = gradObj.has("interpolant") ? gradObj.get("interpolant").getAsString() : "0";
                List<ColorStop> stops = new ArrayList<>();
                if (gradObj.has("colors")) {
                    JsonArray colors = gradObj.getAsJsonArray("colors");
                    for (JsonElement el : colors) {
                        JsonObject stopObj = el.getAsJsonObject();
                        float pos = stopObj.has("position") ? stopObj.get("position").getAsFloat() : 0f;
                        String hex = stopObj.has("color") ? stopObj.get("color").getAsString() : "#ffffff";
                        stops.add(new ColorStop(pos, hex));
                    }
                }
                return new Color("gradient", null, null, null, null, interpolant, stops);
            }

            if (json.has("ramp_gradient")) {
                JsonObject rampObj = json.getAsJsonObject("ramp_gradient");
                String interpolant = rampObj.has("interpolant") ? rampObj.get("interpolant").getAsString() : "0";
                List<ColorStop> stops = new ArrayList<>();
                if (rampObj.has("colors")) {
                    JsonArray colors = rampObj.getAsJsonArray("colors");
                    for (JsonElement el : colors) {
                        JsonObject stopObj = el.getAsJsonObject();
                        float pos = stopObj.has("position") ? stopObj.get("position").getAsFloat() : 0f;
                        String hex = stopObj.has("color") ? stopObj.get("color").getAsString() : "#ffffff";
                        stops.add(new ColorStop(pos, hex));
                    }
                }
                return new Color("ramp_gradient", null, null, null, null, interpolant, stops);
            }

            // 默认为纯色模式
            String r = json.has("r") ? json.get("r").getAsString() : "1";
            String g = json.has("g") ? json.get("g").getAsString() : "1";
            String b = json.has("b") ? json.get("b").getAsString() : "1";
            String a = json.has("a") ? json.get("a").getAsString() : "1";
            return new Color("solid", r, g, b, a, null, null);
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
        private final String color;

        public ColorStop(float position, String color) {
            this.position = position;
            this.color = color;
        }

        public float getPosition() { return position; }
        public String getColor() { return color; }
    }

    // --- Getter ---

    public Color getColor() { return color; }
}
