package cn.solarmoon.spark_core.molang.runtime;

/**
 * Molang 编译路径使用的算术运算实现。
 * <p>
 * 生成的脚本类由 {@link MolangCompiler} 内部的 classloader 定义，与这里的类
 * 不在同一个运行期包中，因此本类及其入口方法必须是 {@code public}。
 * <p>
 * 存在的意义是让编译路径与解释路径共用同一份实现，避免两条路径的运算口径漂移
 * （见 {@code docs/molang_compiled_path_defects_plan.md} §1）。
 */
public final class MolangArithmetic {

    private MolangArithmetic() {}

    /**
     * Molang 口径的除法。
     * <p>
     * Molang allows division by zero, which is always equal to 0.
     *
     * @param dividend 被除数
     * @param divisor  除数，为 0（含 {@code -0.0}）时结果为 0
     * @return 商，除数为 0 时为 0
     */
    public static double divide(final double dividend, final double divisor) {
        if (divisor == 0) {
            return 0;
        }
        return dividend / divisor;
    }
}
