package cn.solarmoon.spark_core

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Spark-Core 配置系统。
 *
 * 使用 NeoForge Common Config（ModConfig.Type.COMMON），
 * 服务端和客户端各自拥有独立的配置文件，互不影响：
 * - 服务端：`<world>/serverconfig/spark_core-common.toml`
 * - 客户端：`.minecraft/config/spark_core-common.toml`
 */
object SparkCoreConfig {

    @JvmStatic
    val SPEC: ModConfigSpec

    // ==================== 物理引擎 ====================
    /** 是否启用单线程物理模式（调试/兼容性诊断用） */
    @JvmStatic
    val SINGLE_THREAD_PHYSICS: ModConfigSpec.BooleanValue

    init {
        val builder = ModConfigSpec.Builder()

        builder.comment(
            "物理引擎配置",
            "Physics engine settings.",
            "单线程模式下物理计算在主线程同步执行，多线程模式（默认）使用独立协程。",
            "In single-thread mode physics runs synchronously on the main thread.",
            "服务端与客户端各自独立配置，互不影响。",
            "Server and client configs are independent."
        )

        SINGLE_THREAD_PHYSICS = builder
            .comment(
                "是否启用单线程物理模式",
                "Enable single-threaded physics mode.",
                "默认关闭（使用独立物理线程）。",
                "Default: false (uses dedicated physics thread).",
                "调试或排查兼容性问题时建议开启。",
                "Useful for debugging and compatibility diagnostics."
            )
            .define("singleThreadPhysics", false)

        SPEC = builder.build()
    }
}
