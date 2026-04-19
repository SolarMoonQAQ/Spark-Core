package cn.solarmoon.spark_core.compat.create

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.ModList
import net.neoforged.neoforge.common.NeoForge

/**
 * Create（机械动力）兼容入口。
 *
 * 设计目标：
 * 1) 保持“可选兼容”而不是“强依赖”：
 *    - 是否启用兼容逻辑由运行时检测 Create 是否加载来决定；
 *    - 未安装 Create 时，本模组仍应按原逻辑正常运行。
 * 2) 统一兼容入口，便于后续在此处继续扩展 Create 相关适配代码。
 */
object CreateCompat {

    /**
     * 机械动力在 NeoForge 环境中的 mod id。
     */
    const val MOD_ID = "create"

    /**
     * 记录当前游戏进程中 Create 是否已加载。
     * 仅在 [init] 中写入，其他地方只读使用。
     */
    var IS_LOADED: Boolean = false
        private set

    /**
     * 记录是否已经向游戏事件总线注册过 Create 兼容事件处理器。
     *
     * 该标志用于避免在客户端与服务端重复初始化阶段发生重复注册。
     */
    private var hooksRegistered = false

    /**
     * 初始化兼容状态，并在满足条件时注册运行时兼容处理器。
     *
     * 线程语义：
     * - 该方法在模组生命周期线程调用；
     * - 仅做轻量状态检查与事件注册，不触发耗时逻辑。
     */
    fun init() {
        IS_LOADED = ModList.get().isLoaded(MOD_ID)
        if (IS_LOADED && !hooksRegistered) {
            // 仅在确认 Create 已加载后再触发类加载，确保“可选兼容”语义安全。
            NeoForge.EVENT_BUS.register(CreateContraptionPhysicsApplier)
            hooksRegistered = true
        }
    }

    /**
     * 仅在 Create 存在时执行传入逻辑的辅助方法。
     *
     * 使用建议：
     * - 在后续具体兼容功能中，可用此方法包裹 Create API 调用；
     * - 可避免各处重复写 `if (IS_LOADED)` 判定。
     *
     * 容错策略：
     * - 兼容逻辑执行异常时会记录警告并返回 null，避免影响主流程稳定性。
     */
    inline fun <T> whenLoaded(action: () -> T): T? {
        if (!IS_LOADED) return null
        return runCatching(action).onFailure { throwable ->
            SparkCore.LOGGER.warn("Create 兼容逻辑执行失败，已回退为非兼容路径。", throwable)
        }.getOrNull()
    }
}

