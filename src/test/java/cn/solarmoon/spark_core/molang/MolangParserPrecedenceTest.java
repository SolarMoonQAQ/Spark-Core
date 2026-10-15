package cn.solarmoon.spark_core.molang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D4：以一元负号开头、被调函数是成员访问的调用（{@code -math.clamp(x, 0, 40)}）的解析。
 * <p>
 * 解析器把负号套在「函数表达式」上（{@code Call(Unary(-)(Access(math, clamp)), args)}），
 * 于是两条求值路径都取不到那个函数、静默返回 0：编译路径走
 * {@code MolangCompilingVisitor#visitCall} 的兜底分支，解释路径走
 * {@code ExpressionInterpreter#visitCall} 的 {@code Value.nil()} 分支。
 * 缺陷现象与影响面见 {@code docs/molang_compiled_path_defects_plan.md} §4。
 * <p>
 * 编译路径经 {@link MolangContextRegistry} 走生产同一条路；解释路径用
 * {@link MochaEngine#eval(String)}。
 *
 * @author Sweetzonzi
 */
class MolangParserPrecedenceTest {

    private static double compiled(final String expression) {
        SparkMolangContext<?> context = new SparkMolangContext<>();
        return MolangContextRegistry.compile(expression, context).evaluate(context);
    }

    private static double interpreted(final String expression) {
        MochaEngine<?> engine = MolangContextRegistry.getOrCreateEngine(SparkMolangContext.class);
        return engine.eval(expression);
    }

    private static void assertBothPaths(final double expected, final String expression) {
        assertEquals(expected, compiled(expression), 1e-9, "编译路径: " + expression);
        assertEquals(expected, interpreted(expression), 1e-9, "解释路径: " + expression);
    }

    @Test
    void negationWrapsTheWholeCall() {
        assertBothPaths(-3.0, "-math.abs(3)");
        assertBothPaths(-7.0, "-math.clamp(10,0,7)");
        assertBothPaths(-3.0, "-math.lerp(0,6,0.5)");
    }

    @Test
    void operatorsAfterTheCallStayOutsideTheNegation() {
        assertBothPaths(-2.0, "-math.abs(3)+1");
        assertBothPaths(-6.0, "-math.abs(3)*2");
    }

    @Test
    void negatedLiteralsAndBinaryMinusAreUnchanged() {
        assertBothPaths(-6.0, "-2*math.abs(3)");
        assertBothPaths(-14.5, "-7.5-math.clamp(10,0,7)");
        assertBothPaths(-7.5, "-7.5-math.clamp(10,0,7)+math.clamp(10,0,7)");
    }

    @Test
    void nestedNegationWorks() {
        assertBothPaths(3.0, "--math.abs(3)");
    }
}
