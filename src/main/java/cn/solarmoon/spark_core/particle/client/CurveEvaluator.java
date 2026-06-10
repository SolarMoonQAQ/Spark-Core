package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.curve.ParticleCurve;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 曲线求值器。在每 tick 对发射器/粒子的曲线进行求值，
 * 将结果写入 Molang 变量供其他组件引用。
 * <p>
 * 所有 Molang 表达式在 {@link ParticleCurve} 构造时已预编译，
 * 此处仅做求值——零运行时编译开销。
 */
public class CurveEvaluator {

    /** 预编译后的曲线条目：变量名 + 已预编译表达式的曲线 */
    private final List<Entry> entries;
    private final ParticleMolangEnvironment molang;

    /**
     * @param curves 曲线定义表（变量名 → 曲线，曲线内的 Molang 表达式应已在构造时预编译）
     * @param molang Molang 求值环境
     */
    public CurveEvaluator(Map<String, ParticleCurve> curves, ParticleMolangEnvironment molang) {
        this.molang = molang;
        this.entries = new ArrayList<>();
        if (curves == null || curves.isEmpty()) return;

        for (Map.Entry<String, ParticleCurve> e : curves.entrySet()) {
            String cleanName = e.getKey().replace("variable.", "").replace("v.", "");
            entries.add(new Entry(cleanName, e.getValue()));
        }
    }

    /** 求值所有曲线（粒子级），将结果写入 Molang 变量。 */
    public void evaluate(ParticleArray buf, int particleIndex) {
        if (entries.isEmpty()) return;

        for (Entry e : entries) {
            ParticleCurve curve = e.curve;
            double inputValue = curve.getCompiledInput().evaluate(molang.getContext());

            float horizontalRange;
            MolangExpression rangeExpr = curve.getCompiledHorizontalRange();
            if (rangeExpr != null) {
                horizontalRange = (float) rangeExpr.evaluate(molang.getContext());
            } else {
                horizontalRange = safeParseFloat(curve.getHorizontalRange());
            }

            float result = curve.evaluate((float) inputValue, horizontalRange);
            molang.setVariable(e.cleanName, result);
        }
    }

    /** 求值所有曲线（发射器级），将结果写入 Molang 变量。 */
    public void evaluateEmitter() {
        if (entries.isEmpty()) return;

        for (Entry e : entries) {
            ParticleCurve curve = e.curve;
            double inputValue = curve.getCompiledInput().evaluate(molang.getContext());

            float horizontalRange;
            MolangExpression rangeExpr = curve.getCompiledHorizontalRange();
            if (rangeExpr != null) {
                horizontalRange = (float) rangeExpr.evaluate(molang.getContext());
            } else {
                horizontalRange = safeParseFloat(curve.getHorizontalRange());
            }

            float result = curve.evaluate((float) inputValue, horizontalRange);
            molang.setVariable(e.cleanName, result);
        }
    }

    private static float safeParseFloat(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 曲线条目：纯数据容器，持有已预编译的曲线 */
    private static class Entry {
        final String cleanName;
        final ParticleCurve curve;

        Entry(String cleanName, ParticleCurve curve) {
            this.cleanName = cleanName;
            this.curve = curve;
        }
    }
}
