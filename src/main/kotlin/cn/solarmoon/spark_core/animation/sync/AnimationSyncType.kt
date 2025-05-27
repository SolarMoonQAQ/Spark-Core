package cn.solarmoon.spark_core.animation.sync

/**
 * 定义动画数据同步的类型
 */
enum class AnimationSyncType {
    /**
     * 全量批量同步，通常用于初始加载或玩家首次加入
     */
    FULL_BATCH,

    /**
     * 增量更新/添加单个或多个动画
     */
    INCREMENTAL_UPDATE,

    /**
     * 增量移除单个或多个动画
     */
    INCREMENTAL_REMOVE
}
