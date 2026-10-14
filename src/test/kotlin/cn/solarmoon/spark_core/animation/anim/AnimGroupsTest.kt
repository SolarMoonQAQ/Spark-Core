package cn.solarmoon.spark_core.animation.anim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 设施 C 的编译期/取值闸门 —— 常驻低位段整体低于全部语义层。 */
class AnimGroupsTest {

    @Test
    fun persistentSlotsSitBelowAllSemanticLayers() {
        assertEquals(-8, AnimGroups.PERSISTENT_BASE)
        assertEquals(8, AnimGroups.PERSISTENT_SLOTS)
        assertEquals(-8, AnimGroups.persistent(0))
        assertEquals(-1, AnimGroups.persistent(7))
        assertTrue(AnimGroups.persistent(7) < AnimGroups.AMBIENT)
    }

    @Test
    fun persistentRejectsOutOfRangeSlots() {
        assertThrows(IllegalArgumentException::class.java) { AnimGroups.persistent(-1) }
        assertThrows(IllegalArgumentException::class.java) { AnimGroups.persistent(AnimGroups.PERSISTENT_SLOTS) }
    }
}
