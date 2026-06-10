package cn.solarmoon.spark_core.particle.common.data.curve;

import java.util.List;

/**
 * 粒子曲线定义。对应基岩版 JSON 中的 curves 对象。
 * 支持 4 种插值类型: linear, bezier, catmull_rom, bezier_chain。
 */
public class ParticleCurve {
    private final CurveType type;
    private final String input;          // Molang 表达式字符串
    private final String horizontalRange; // 可为数值字符串或 MoLang 表达式（如 "variable.particle_lifetime"）
    private final List<Float> nodes;

    public ParticleCurve(CurveType type, String input, String horizontalRange, List<Float> nodes) {
        this.type = type;
        this.input = input;
        this.horizontalRange = horizontalRange;
        this.nodes = nodes;
    }

    public CurveType getType() { return type; }
    public String getInput() { return input; }
    public String getHorizontalRange() { return horizontalRange; }
    public List<Float> getNodes() { return nodes; }

    public enum CurveType {
        LINEAR,
        BEZIER,
        CATMULL_ROM,
        BEZIER_CHAIN
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
        // 三次贝塞尔: 需要 4 个控制点 (P0, P1, P2, P3)，实际 P0=0, P3=1
        if (nodes.size() < 4) return evaluateLinear(t);
        float u = 1 - t;
        float p0 = nodes.get(0), p1 = nodes.get(1), p2 = nodes.get(2), p3 = nodes.get(3);
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    private float evaluateCatmullRom(float t) {
        if (nodes.size() < 4) return evaluateLinear(t);
        // 将 t 映射到分段 catmull-rom
        float pos = t * (nodes.size() - 3); // 有 n-3 段（每段 4 控制点）
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
        // Bezier chain: 每 3 个节点一组 (P1, P2, P3)，P0 是上一组的 P3
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
