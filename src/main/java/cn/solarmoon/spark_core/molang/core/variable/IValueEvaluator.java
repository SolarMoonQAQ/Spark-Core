package cn.solarmoon.spark_core.molang.core.variable;

public interface IValueEvaluator<TValue, TContext> {
    TValue eval(TContext ctx);
}
