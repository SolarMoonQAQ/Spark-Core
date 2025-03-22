package cn.solarmoon.spark_core.molang.core;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.molang.core.binding.PrimaryBinding;
import cn.solarmoon.spark_core.molang.core.value.DoubleValue;
import cn.solarmoon.spark_core.molang.core.value.IValue;
import cn.solarmoon.spark_core.molang.core.value.MolangValue;
import cn.solarmoon.spark_core.molang.engine.MolangEngine;
import cn.solarmoon.spark_core.molang.engine.parser.ParseException;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding;

import java.util.Map;

public class MolangParser {

    private final Map<String, ObjectBinding> extraBindings;
    private MolangEngine engine;
    private PrimaryBinding primaryBinding;

    public MolangParser(Map<String, ObjectBinding> extraBindings) {
        this.extraBindings = extraBindings;
    }

    public void init() {
        primaryBinding = new PrimaryBinding(extraBindings);
        engine = MolangEngine.fromCustomBinding(primaryBinding);
    }

    @SuppressWarnings("unused")
    public IValue parseExpression(String molangExpression) {
        try {
            return parseExpressionUnsafe(molangExpression);
        } catch (Exception e) {
            SparkCore.LOGGER.error("Failed to parse value \"{}\": {}", molangExpression, e.getMessage());
            return DoubleValue.ZERO;
        }
    }

    public IValue parseExpressionUnsafe(String molangExpression) throws ParseException {
        init();
        MolangValue value = new MolangValue(engine.parse(molangExpression), molangExpression);
        primaryBinding.popStackFrame();
        return value;
    }

    @SuppressWarnings("unused")
    public IValue getConstant(double value) {
        return new DoubleValue(value);
    }

    public void reset() {
        primaryBinding.reset();
    }
}
