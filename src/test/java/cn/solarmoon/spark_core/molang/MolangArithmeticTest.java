package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.molang.runtime.MolangArithmetic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1：编译路径与解释路径共用的除法实现，除零口径按 Molang 规范（恒为 0）。
 * <p>
 * 见 {@code docs/molang_compiled_path_defects_plan.md} §1 与 §6 的 A1 组。
 */
class MolangArithmeticTest {

    @Test
    void divisionByZeroIsAlwaysZero() {
        assertEquals(0.0, MolangArithmetic.divide(1.0, 0.0));
        assertEquals(0.0, MolangArithmetic.divide(0.0, 0.0));
        assertEquals(0.0, MolangArithmetic.divide(-1.0, 0.0));
        assertEquals(0.0, MolangArithmetic.divide(1.0, -0.0));
    }

    @Test
    void normalDivisionIsUnchanged() {
        assertEquals(2.5, MolangArithmetic.divide(5.0, 2.0));
        assertEquals(-2.0, MolangArithmetic.divide(-6.0, 3.0));
        assertTrue(Double.isFinite(MolangArithmetic.divide(5.0, 2.0)));
    }
}
