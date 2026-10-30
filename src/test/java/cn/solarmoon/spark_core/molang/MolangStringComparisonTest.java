package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.molang.runtime.MolangContext;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import cn.solarmoon.spark_core.molang.runtime.MolangStringExpression;
import cn.solarmoon.spark_core.molang.runtime.binding.JavaObjectBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.StringQueryBinding;
import cn.solarmoon.spark_core.molang.runtime.standard.MochaMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ==} / {@code !=} 的字符串口径。
 * <ul>
 *   <li>任一侧是字符串型表达式时按文本比较，两侧都按 String 编译。</li>
 *   <li>两侧都不是字符串时仍按 double 比较，原有数值语义不变。</li>
 *   <li>解释路径与编译路径给出同一结果。</li>
 *   <li>比较结果在 String 目标下真的推入 String，不会在栈上留下 int。</li>
 * </ul>
 */
public class MolangStringComparisonTest {

    /**
     * 测试上下文：{@code q.state} / {@code q.absent} 是字符串查询，
     * {@code q.ammo} 是数值查询。类与查询方法必须是 public：
     * 生成的脚本类与查询方法的声明类在不同 classloader 里。
     */
    public static class TestContext extends MolangContext<Object> {
        private String state = "ready";
        private double ammo;

        public TestContext() {
            super();
        }

        public void setState(String state) {
            this.state = state;
        }

        public void setAmmo(double ammo) {
            this.ammo = ammo;
        }

        @StringQueryBinding("state")
        public String queryState() {
            return state;
        }

        /** 缺省值：null 表示该查询没有值。 */
        @StringQueryBinding("absent")
        public String queryAbsent() {
            return null;
        }

        @QueryBinding("ammo")
        public double queryAmmo() {
            return ammo;
        }
    }

    private static MochaEngine<TestContext> newEngine(TestContext ctx) {
        return MochaEngine.create(ctx, builder ->
                builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath())));
    }

    private static double compileAndEval(MochaEngine<TestContext> engine, TestContext ctx, String code) {
        return engine.compile(code, MolangExpression.class).evaluate(ctx);
    }

    // ==================== 编译路径：字符串查询 ====================

    @Test
    void queryStringComparesByText() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        assertEquals(1.0, compileAndEval(engine, ctx, "q.state=='ready'"), "同文本应相等");
        assertEquals(0.0, compileAndEval(engine, ctx, "q.state=='empty'"), "不同文本不得相等");
        assertEquals(0.0, compileAndEval(engine, ctx, "q.state!='ready'"), "同文本的 != 应为假");
        assertEquals(1.0, compileAndEval(engine, ctx, "q.state!='empty'"), "不同文本的 != 应为真");

        ctx.setState("empty");
        assertEquals(1.0, compileAndEval(engine, ctx, "q.state=='empty'"), "状态变化后按新文本比较");
        assertEquals(0.0, compileAndEval(engine, ctx, "q.state=='ready'"));
    }

    @Test
    void missingStringOnlyEqualsMissingString() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        // 查询返回 null 时不得等于任何具体文本，?? 兜底后按兜底值比较
        assertEquals(0.0, compileAndEval(engine, ctx, "q.absent=='N'"));
        assertEquals(1.0, compileAndEval(engine, ctx, "(q.absent??'N')=='N'"));
        assertEquals(0.0, compileAndEval(engine, ctx, "(q.absent??'N')=='R'"));
    }

    @Test
    void stringAndNumberCompareAsText() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        // 文字面量一侧是字符串 → 数字按 String.valueOf(double) 转文本
        assertEquals(1.0, compileAndEval(engine, ctx, "'1.0'==1"));
        assertEquals(0.0, compileAndEval(engine, ctx, "'1'==1"));
        // 字符串查询与数字混比同理：q.state 不可能等于数字文本
        assertEquals(0.0, compileAndEval(engine, ctx, "q.state==1"));
    }

    @Test
    void variablesAndTempSlotsCompareAsText() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        // 变量与 temp 槽在编译路径里只存 double：与字符串比较时按 String.valueOf(double) 转文本
        assertEquals(1.0, compileAndEval(engine, ctx, "v.gear=='0.0'"));
        assertEquals(0.0, compileAndEval(engine, ctx, "v.gear=='ready'"));
        assertEquals(1.0, compileAndEval(engine, ctx, "temp.gear=='0.0'"));
        assertEquals(0.0, compileAndEval(engine, ctx, "temp.gear=='ready'"));
    }

    // ==================== 编译路径：数值比较不变 ====================

    @Test
    void numericComparisonKeepsDoubleSemantics() {
        TestContext ctx = new TestContext();
        ctx.setAmmo(0.0);
        MochaEngine<TestContext> engine = newEngine(ctx);

        assertEquals(1.0, compileAndEval(engine, ctx, "q.ammo==0"));
        assertEquals(0.0, compileAndEval(engine, ctx, "q.ammo!=0"));
        assertEquals(1.0, compileAndEval(engine, ctx, "math.abs(q.ammo)<=0"));
    }

    // ==================== 解释路径与编译路径一致 ====================

    @Test
    void interpretedStringComparisonMatchesCompiledPath() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        for (String code : new String[]{"'a'=='b'", "'a'=='a'", "'a'!='b'", "'a'!='a'", "'1.0'==1", "'1'==1"}) {
            assertEquals(engine.eval(code), compileAndEval(engine, ctx, code), code);
        }
        // 解释路径的字符串比较不再退化成 getAsNumber()==getAsNumber()
        assertEquals(0.0, engine.eval("'ready'=='empty'"));
        assertEquals(1.0, engine.eval("'ready'=='ready'"));
    }

    // ==================== String 目标下的比较结果 ====================

    @Test
    void comparisonResultIsAStringUnderStringTarget() {
        TestContext ctx = new TestContext();
        MochaEngine<TestContext> engine = newEngine(ctx);

        // 声明类型与栈值必须一致：String 目标下推入 "1"/"0"，而不是 int
        assertEquals("1", engine.compile("q.state=='ready'", MolangStringExpression.class).evaluate(ctx));
        assertEquals("0", engine.compile("q.state=='empty'", MolangStringExpression.class).evaluate(ctx));
        assertEquals("1", engine.compile("q.ammo==0", MolangStringExpression.class).evaluate(ctx));
        // 字符串比较可直接放进字符串三元
        assertEquals("Y", engine.compile("q.state=='ready'?'Y':'N'", MolangStringExpression.class).evaluate(ctx));
        assertEquals("N", engine.compile("q.state=='empty'?'Y':'N'", MolangStringExpression.class).evaluate(ctx));
    }
}
