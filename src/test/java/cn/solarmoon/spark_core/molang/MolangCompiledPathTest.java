package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.molang.runtime.MolangContext;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import cn.solarmoon.spark_core.molang.runtime.binding.JavaObjectBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.standard.MochaMath;
import cn.solarmoon.spark_core.molang.runtime.value.MutableObjectBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2/A3：编译路径的行为回归。
 * <ul>
 *   <li>A2（D1）：含 {@code query.*} 的除法走编译路径，除零结果与解释路径一致（0，不是 NaN）。</li>
 *   <li>A3（D2）：多级左值赋值不再产出空方法体；未实现的算子/形状在编译期响亮失败。</li>
 * </ul>
 * 见 {@code docs/molang_compiled_path_defects_plan.md} §1、§2 与 §6。
 */
public class MolangCompiledPathTest {

    /**
     * 测试上下文：{@code query.x} 绑定到一个可配置的 double。
     * <p>
     * 类与构造必须 {@code public}：生成的脚本类与查询方法的声明类在不同
     * classloader / 运行期包里，靠 public 访问。
     */
    public static class TestContext extends MolangContext<Object> {
        private double x;

        public TestContext() {
            super();
        }

        public void setX(double x) {
            this.x = x;
        }

        @QueryBinding("x")
        public double queryX() {
            return x;
        }
    }

    private static MochaEngine<TestContext> newEngine(TestContext ctx) {
        MochaEngine<TestContext> engine = MochaEngine.create(ctx, builder ->
                builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath())));
        // 解释路径靠 scope 里的 v/variable 解析；编译路径的 v.* 走 context.getVariableStorage()
        engine.scope().set("v", ctx.getVariableStorage());
        engine.scope().set("variable", ctx.getVariableStorage());
        return engine;
    }

    private static double compileAndEval(MochaEngine<TestContext> engine, TestContext ctx, String code) {
        return engine.compile(code, MolangExpression.class).evaluate(ctx);
    }

    // ==================== A2：除零口径 ====================

    @Test
    void compiledDivisionByZeroMatchesInterpreter() {
        TestContext ctx = new TestContext();
        ctx.setX(0.0);
        MochaEngine<TestContext> engine = newEngine(ctx);

        double compiled = compileAndEval(engine, ctx, "q.x/math.abs(q.x)");
        double interpreted = engine.eval("q.x/math.abs(q.x)");

        assertEquals(0.0, compiled, "编译路径 0/0 应为 0，而不是 NaN");
        assertEquals(compiled, interpreted, "两条路径必须给出同一结果");
        assertTrue(Double.isFinite(compiled));
    }

    @Test
    void divisionByZeroIsZeroInBothPaths() {
        TestContext ctx = new TestContext();
        ctx.setX(0.0);
        MochaEngine<TestContext> engine = newEngine(ctx);

        // q.x 未绑定在 scope 上，不会被编译期常量折叠，真的走编译路径
        assertEquals(0.0, compileAndEval(engine, ctx, "q.x/0"));
        // 纯字面量走常量折叠（解释器），口径必须一致
        assertEquals(0.0, compileAndEval(engine, ctx, "1/0"));
    }

    @Test
    void contentPackExpressionStaysFiniteAtZeroSpeed() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);
        // 内容包 bmpt_main 的 jump:LeftArm 旋转分量原文（query.vertical_speed → q.x）
        final String code = "-30+(q.x/math.abs(q.x)*(1-math.exp(-0.1*math.abs(q.x)))*30)";

        ctx.setX(0.0);
        double atZero = compileAndEval(engine, ctx, code);
        assertTrue(Double.isFinite(atZero), "v=0 不得产出 NaN/Infinity: " + atZero);
        // 0/0 按 Molang 口径为 0，第二因子 (1-exp(0)) 也为 0，只剩外层 -30
        assertEquals(-30.0, atZero, 1e-9);

        ctx.setX(3.0);
        double atThree = compileAndEval(engine, ctx, code);
        assertTrue(Double.isFinite(atThree), "v=3 必须有限: " + atThree);
        assertEquals(-22.224546620451537, atThree, 1e-6);
    }

    // ==================== A3：多级左值赋值 ====================

    @Test
    void multiLevelAssignmentWritesThroughObjectValue() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);
        MutableObjectBinding roaming = new MutableObjectBinding();
        ctx.getVariableStorage().set("roaming", roaming);

        double result = compileAndEval(engine, ctx, "v.roaming.leg_armor=1;");

        assertEquals(1.0, result, "赋值表达式的值应为右值");
        assertEquals(1.0, roaming.get("leg_armor").getAsNumber(), "v.roaming.leg_armor 应写回 1");
        assertEquals(1.0, engine.eval("v.roaming.leg_armor"), "解释路径应读回同一变量");
    }

    @Test
    void singleLevelAssignmentStillWorks() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        double result = compileAndEval(engine, ctx, "v.STB_pitch=1;");

        assertEquals(1.0, result);
        assertEquals(1.0, ctx.getVariableStorage().get("STB_pitch").getAsNumber());
    }

    // ==================== A3：未实现形状必须响亮失败 ====================

    @Test
    void unsupportedOperatorFailsLoudlyInsteadOfEmittingBrokenBytecode() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        // ARROW 没有编译实现；x 未绑定在 scope 上，因此不会被常量折叠掉
        IllegalArgumentException arrow = assertThrows(IllegalArgumentException.class,
                () -> engine.compile("x->x", MolangExpression.class));
        assertTrue(arrow.getMessage().contains("ARROW"), arrow.getMessage());
        assertTrue(arrow.getMessage().contains("Identifier(x)"), arrow.getMessage());

        // 非 variable 命名空间的多级赋值形状同样在编译期报错
        IllegalArgumentException assign = assertThrows(IllegalArgumentException.class,
                () -> engine.compile("math.foo=1;", MolangExpression.class));
        assertTrue(assign.getMessage().contains("ASSIGN"), assign.getMessage());
        assertTrue(assign.getMessage().contains("Access"), assign.getMessage());
    }
}
