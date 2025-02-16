package cn.solarmoon.spark_core.molang.engine.parser.ast;

import cn.solarmoon.spark_core.molang.core.util.StringPool;
import org.jetbrains.annotations.NotNull;

public class StructAccessExpression implements Expression {
    private final Expression left;
    private final int path;

    public StructAccessExpression(Expression left, String path) {
        this.left = left;
        this.path = StringPool.computeIfAbsent(path);
    }

    @Override
    public <R> R visit(@NotNull ExpressionVisitor<R> visitor) {
        return visitor.visitStruct(this);
    }

    public Expression left() {
        return left;
    }

    public int path() {
        return path;
    }
}
