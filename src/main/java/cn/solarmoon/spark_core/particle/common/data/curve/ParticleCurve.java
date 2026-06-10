package cn.solarmoon.spark_core.particle.common.data.curve;

import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 粒子曲线定义。对应基岩版 JSON 中的 curves 对象。
 * 支持 4 种插值类型: linear, bezier, catmull_rom, bezier_chain。
 * <p>
 * Molang 表达式字段（{@code input}、{@code horizontalRange}）在构造时即预编译为
 * {@link MolangExpression}，避免每 tick 重复调用 {@code computeIfAbsent} 缓存查找。
 */
public class ParticleCurve {
    private final CurveType type;
    private final String input;                 // Molang 表达式原始字符串
    private final String horizontalRange;       // 可为数值字符串或 MoLang 表达式
    private final List<Float> nodes;

    /** input 表达式预编译结果。空 input 时使用 ZERO。 */
    private final MolangExpression compiledInput;
    /**
     * horizontalRange 表达式预编译结果。
     * 为 null 时表示 horizontalRange 是纯数字，直接 parse 即可。
     */
    @Nullable
    private final MolangExpression compiledHorizontalRange;

    /**
     * 构造曲线并预编译 Molang 表达式。
     *
     * @param type            插值类型
     * @param input           Molang 表达式字符串（如 "v.particle_age / v.particle_lifetime"）
     * @param horizontalRange 数值字符串或 MoLang 表达式（如 "variable.particle_lifetime"）
     * @param nodes           控制点数组
     * @param compiler         表达式编译器（函数式接口，避免 common 包依赖 client 包）
     */
    public ParticleCurve(CurveType type, String input, String horizontalRange,
                         List<Float> nodes, MolangCompiler compiler) {
        this.type = type;
        this.input = input;
        this.horizontalRange = horizontalRange;
        this.nodes = nodes;

        // 预编译 input 表达式
        if (input != null && !input.isEmpty()) {
            this.compiledInput = compiler.compile(input);
        } else {
            this.compiledInput = MolangExpression.zero();
        }

        // 预编译 horizontalRange（若为非纯数字）
        MolangExpression compiledRange = null;
        if (horizontalRange != null && !horizontalRange.isEmpty()) {
            try {
                Float.parseFloat(horizontalRange);
                // 纯数字 → 保持 null，运行时直接 parse
            } catch (NumberFormatException e) {
                compiledRange = compiler.compile(horizontalRange);
            }
        }
        this.compiledHorizontalRange = compiledRange;
    }

    public CurveType getType() { return type; }
    public String getInput() { return input; }
    public String getHorizontalRange() { return horizontalRange; }
    public List<Float> getNodes() { return nodes; }

    /** 获取预编译的 input 表达式（永不为 null，空 input 时为 ZERO） */
    public MolangExpression getCompiledInput() { return compiledInput; }
    /** 获取预编译的 horizontalRange 表达式。null 表示值为纯数字。 */
    @Nullable
    public MolangExpression getCompiledHorizontalRange() { return compiledHorizontalRange; }

    public enum CurveType {
        LINEAR,
        BEZIER,
        CATMULL_ROM,
        BEZIER_CHAIN
    }

    /** 表达式编译器抽象。由调用方（client 包）注入实现，避免 common 包依赖 MolangContextRegistry。 */
    @FunctionalInterface
    public interface MolangCompiler {
        MolangExpression compile(String expression);
    }

    /** 对给定输入值进行曲线求值，horizontalRange 为已解析的数值 */
    public float evaluate(float inputValue, float resolvedHorizontalRange) {
        if (nodes.isEmpty()) return 0;
        if (nodes.size() == 1) return nodes.get(0);

        float t = resolvedHorizontalRange > 0 ? inputValue / resolvedHorizontalRange : 0;
        t = Math.max(0, Math.min(1, t));

        return switch (type) {
            case LINEAR -> evaluateLinear(t);
            case BEZIER -> evaluateBezier(t);
            case CATMULL_ROM -> evaluateCatmullRom(t);
            case BEZIER_CHAIN -> evaluateBezierChain(t);
        };
    }

    private float evaluateLinear(float t) {
        float pos = t * (nodes.size() - 1);
        int i = (int) pos;
        float frac = pos - i;
        if (i >= nodes.size() - 1) return nodes.get(nodes.size() - 1);
        return nodes.get(i) + (nodes.get(i + 1) - nodes.get(i)) * frac;
    }

    private float evaluateBezier(float t) {
        if (nodes.size() < 4) return evaluateLinear(t);
        float u = 1 - t;
        float p0 = nodes.get(0), p1 = nodes.get(1), p2 = nodes.get(2), p3 = nodes.get(3);
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    private float evaluateCatmullRom(float t) {
        if (nodes.size() < 4) return evaluateLinear(t);
        float pos = t * (nodes.size() - 3);
        int i = Math.min((int) pos, nodes.size() - 4);
        float frac = pos - i;
        float p0 = nodes.get(Math.max(0, i - 1));
        float p1 = nodes.get(i);
        float p2 = nodes.get(i + 1);
        float p3 = nodes.get(Math.min(nodes.size() - 1, i + 2));
        float t2 = frac * frac;
        float t3 = t2 * frac;
        return 0.5f * ((2 * p1) + (-p0 + p2) * frac + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private float evaluateBezierChain(float t) {
        if (nodes.size() < 3) return evaluateLinear(t);
        int segments = (nodes.size() - 1) / 3;
        if (segments < 1) return evaluateLinear(t);
        float pos = t * segments;
        int seg = Math.min((int) pos, segments - 1);
        float frac = pos - seg;
        int idx = seg * 3;
        float p0 = seg == 0 ? nodes.get(0) : nodes.get(idx);
        float p1 = nodes.get(idx + 1);
        float p2 = nodes.get(idx + 2);
        float p3 = seg == segments - 1 ? nodes.get(idx + 2) : nodes.get(idx + 3);
        float u = 1 - frac;
        return u * u * u * p0 + 3 * u * u * frac * p1 + 3 * u * frac * frac * p2 + frac * frac * frac * p3;
    }
}
