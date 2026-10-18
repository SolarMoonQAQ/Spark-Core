package cn.solarmoon.spark_core.pack.diagnostic

import net.minecraft.network.chat.Component

enum class PackSide { CLIENT, SERVER }

enum class PackOrigin { LOCAL, REMOTE }

/**
 * 一条完整加载路径的“端 + 来源”。
 * 既决定会话清空范围，也决定 [PackIssue] 的归属。
 */
enum class PackLoadContext(
    val side: PackSide,
    val origin: PackOrigin,
) {
    /** 客户端本地资源包：启动预加载、客户端资源重载 */
    CLIENT_LOCAL(PackSide.CLIENT, PackOrigin.LOCAL),

    /** 客户端接收服务端包：配置阶段 payload、reload payload */
    CLIENT_REMOTE(PackSide.CLIENT, PackOrigin.REMOTE),

    /** 服务端本地包：启动、资源重载、/spark package reload */
    SERVER_LOCAL(PackSide.SERVER, PackOrigin.LOCAL),

    /** 预留：当前无调用；避免以后再加一个“来源”维度 */
    SERVER_REMOTE(PackSide.SERVER, PackOrigin.REMOTE),
    ;

    val isClientSide get() = side == PackSide.CLIENT

    /**
     * 传给 [cn.solarmoon.spark_core.pack.modules.SparkPackModule] 的 fromServer，
     * 保留现有模块“权威读取”语义：客户端本地包为 false，其余为 true。
     * 注意：来源看 [origin]，不要把这个布尔当来源。
     */
    val fromServer get() = this != CLIENT_LOCAL
}

enum class PackSeverity { WARN, ERROR }

/**
 * 一条内容包问题记录。
 *
 * @property moduleId 顶层分组键
 * @property packId   非包来源为 null
 * @property location `"<namespace>:<moduleId>/<path>"`，或注册 id / `"*"`（模块级）
 */
data class PackIssue(
    val moduleId: String,
    val packId: String?,
    val location: String,
    val severity: PackSeverity,
    val message: Component,
    val cause: Throwable?,
    val context: PackLoadContext,
)

data class PackModuleIssues(
    val moduleId: String,
    val issues: List<PackIssue>,
) {
    val errors get() = issues.count { it.severity == PackSeverity.ERROR }
    val warnings get() = issues.count { it.severity == PackSeverity.WARN }
}

data class PackIssueSummary(
    val modules: List<PackModuleIssues>,
    val errors: Int,
    val warnings: Int,
    val dropped: Int,
) {
    val isEmpty get() = errors == 0 && warnings == 0 && dropped == 0
}
