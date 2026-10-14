package cn.solarmoon.spark_core.api

import cn.solarmoon.spark_core.LevelPatch
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.minecraft.world.level.Level
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * 设施 D 的编译期闸门 —— 只验证可空入口在 Java 与 Kotlin 两侧都可被引用，
 * 不实际求值（真实 Level 需要已初始化的 PhysicsLevel）。
 */
class PhysicsLevelReadyEntryTest {

    @Test
    fun nullableEntryIsReferenceableFromKotlinAndJavaSurface() {
        val kotlinSurface: (Level) -> PhysicsLevel? = { it.physicsLevelOrNull }
        val javaSurface: (LevelPatch) -> PhysicsLevel? = { it.physicsLevelOrNull }
        assertNotNull(kotlinSurface)
        assertNotNull(javaSurface)
    }
}
