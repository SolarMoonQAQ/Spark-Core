package cn.solarmoon.spark_core.resource.graph

/**
 * 定义图中顶点之间的关系类型。
 */
enum class EdgeType {
    /**
     * 硬依赖关系。如果依赖项不存在，则资源加载失败。
     */
    HARD_DEPENDENCY,

    /**
     * 软依赖关系。如果依赖项不存在，则只记录警告，资源继续加载。
     */
    SOFT_DEPENDENCY,

    /**
     * 覆盖关系。表示一个资源覆盖另一个资源。
     */
    OVERRIDE
} 