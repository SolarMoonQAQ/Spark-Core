package cn.solarmoon.spark_core.molang.core.value;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.molang.engine.parser.ast.Expression;
import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator;
import com.mojang.serialization.Codec;

import java.util.List;
import java.util.function.Supplier;

public class MolangValue implements IValue {
    private final String originalExpressions;//Molang表达式的原始字符串
    private final Expression[] expressions;//待计算的molang表达式
    public static final Codec<IValue> MOLANG_VALUE_CODEC = Codec.lazyInitialized(() -> Codec.stringResolver(MolangValue::deParse, MolangValue::parse));

    public MolangValue(List<Expression> expressions, String originalExpressions) {
        this.expressions = expressions.toArray(new Expression[0]);
        this.originalExpressions = originalExpressions;
    }

    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) throws Exception {
        Object lastResult = 0d;

        for (Expression expression : expressions) {
            lastResult = evaluator.eval(expression);
            Object returnValue = evaluator.popReturnValue();
            if (returnValue != null) {
                lastResult = returnValue;
                break;
            }
        }

        return lastResult;
    }

    /**
     * 解析String为molang表达式，存入IValue对象
     * @param expression molang表达式字符串
     * @return IValue对象
     */
    public static IValue parse(String expression) {
        return SparkCore.PARSER.parseExpression(expression);
    }

    /**
     * 编码IValue对象为String
     * @param value IValue对象，存有molang表达式
     * @return 编码后的字符串
     */
    public static String deParse(IValue value) {
        if (value instanceof MolangValue) return ((MolangValue) value).getOriginalExpressions();
        else return "0";
    }

    public String getOriginalExpressions() {
        return originalExpressions;
    }

}
