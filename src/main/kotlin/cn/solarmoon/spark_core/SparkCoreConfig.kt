package cn.solarmoon.spark_core

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * 客户端内容包问题提示模式。
 */
enum class PackIssueNotificationMode {
    /** 不发送聊天提示，日志仍然完整 */
    OFF,

    /** 一条摘要 */
    SUMMARY,

    /** 按模块列出前几条，最多 10 行（默认） */
    DETAIL,
}

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

    // ==================== 内容包诊断 ====================
    /** 客户端进入游戏/重载时如何提示内容包读取问题 */
    @JvmStatic
    val PACK_ISSUE_NOTIFICATION: ModConfigSpec.EnumValue<PackIssueNotificationMode>

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

        builder.comment(
            "内容包诊断",
            "Content pack diagnostics.",
            "控制客户端进入游戏/重载时如何提示内容包读取问题。",
            "Controls how content pack read issues are reported in chat on the client.",
        )

        PACK_ISSUE_NOTIFICATION = builder
            .comment(
                "提示模式：OFF（关闭聊天提示）、SUMMARY（一条摘要）、DETAIL（按模块最多列出 10 条，默认）",
                "Notification mode: OFF, SUMMARY, DETAIL (up to 10 lines, default).",
                "无论该值如何，日志都会保留完整记录。",
                "Logs keep the full record regardless of this value.",
            )
            .defineEnum("packIssueNotification", PackIssueNotificationMode.DETAIL)

        SPEC = builder.build()
    }
}
