package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.curve.ParticleCurve;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;

import java.util.Map;

/**
 * 曲线求值器。在每 tick 对发射器/粒子的曲线进行求值，
 * 将结果写入 Molang 变量供其他组件引用。
 */
public class CurveEvaluator {

    private final Map<String, ParticleCurve> curves;
    private final ParticleMolangEnvironment molang;

    public CurveEvaluator(Map<String, ParticleCurve> curves, ParticleMolangEnvironment molang) {
        this.curves = curves;
        this.molang = molang;
    }

    /**
     * 求值所有曲线，将结果写入变量。
     */
    public void evaluate(ParticleArray buf, int particleIndex) {
        if (curves == null || curves.isEmpty()) return;

        for (Map.Entry<String, ParticleCurve> entry : curves.entrySet()) {
            String varName = entry.getKey();  // e.g. "variable.curve_size"
            ParticleCurve curve = entry.getValue();

            // 求值曲线的 input Molang 表达式
            double inputValue = 0;
            if (!curve.getInput().isEmpty()) {
                MolangExpression expr = molang.compile(curve.getInput());
                inputValue = molang.evaluate(expr);
            }

            // 曲线插值
            float result = curve.evaluate((float) inputValue);

            // 写入变量 (去掉 "variable." 或 "v." 前缀)
            String cleanName = varName.replace("variable.", "").replace("v.", "");
            molang.setVariable(cleanName, result);
        }
    }

    /**
     * 求值所有曲线（发射器级，无粒子索引）。
     */
    public void evaluateEmitter() {
        if (curves == null || curves.isEmpty()) return;

        for (Map.Entry<String, ParticleCurve> entry : curves.entrySet()) {
            String varName = entry.getKey();
            ParticleCurve curve = entry.getValue();

            double inputValue = 0;
            if (!curve.getInput().isEmpty()) {
                MolangExpression expr = molang.compile(curve.getInput());
                inputValue = molang.evaluate(expr);
            }

            float result = curve.evaluate((float) inputValue);

            String cleanName = varName.replace("variable.", "").replace("v.", "");
            molang.setVariable(cleanName, result);
        }
    }
}
