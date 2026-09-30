package cn.solarmoon.spark_core.particle.common.data.component;

import cn.solarmoon.spark_core.particle.common.data.IEmitterComponentDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 发射器形状组件的抽象适配器基类。<br>
 * 所有形状组件继承此类，共用 offset 和 direction 字段。
 */
public abstract class EmitterShapeAdapter implements IEmitterComponentDefinition {

    protected final String[] offset;
    protected final String[] direction;

    public EmitterShapeAdapter(String[] offset, String[] direction) {
        this.offset = offset;
        this.direction = direction;
    }

    @Override
    public int order() {
        return 0;
    }

    public String[] getOffset() {
        return offset;
    }

    public String[] getDirection() {
        return direction;
    }

    // ==================== 反序列化辅助 ====================

    /**
     * 安全读取三元素数组字段。值不是数组时返回默认值，**不抛异常** ——
     * {@code getAsJsonArray()} 在字符串/数字值上抛 {@code ClassCastException}，
     * 而反序列化抛异常会让整个粒子定义加载失败（游戏内表现为该效果整体缺失）。
     */
    protected static String[] readVec3(JsonObject json, String key, String defX, String defY, String defZ) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.size() >= 3) {
                return new String[]{arr.get(0).getAsString(), arr.get(1).getAsString(), arr.get(2).getAsString()};
            }
        }
        return new String[]{defX, defY, defZ};
    }

    /**
     * 读取 {@code direction}。Bedrock 允许该字段是三元素向量，也允许是字符串
     * {@code "inwards"} / {@code "outwards"}（径向方向）。字符串形式下方向向量取零
     * —— 径向分量由形状运行时给出，朝向由 {@link #readDirectionInwards(JsonObject)} 解释。
     */
    protected static String[] readDirection(JsonObject json, String defX, String defY, String defZ) {
        JsonElement element = json.get("direction");
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new String[]{"0", "0", "0"};
        }
        return readVec3(json, "direction", defX, defY, defZ);
    }

    /**
     * 读取径向朝向：{@code direction} 为字符串 {@code "inwards"} 时为真，
     * 否则回退到 {@code direction_inwards} 布尔字段。
     */
    protected static boolean readDirectionInwards(JsonObject json) {
        JsonElement element = json.get("direction");
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return "inwards".equalsIgnoreCase(element.getAsString());
        }
        return json.has("direction_inwards") && json.get("direction_inwards").getAsBoolean();
    }
}
