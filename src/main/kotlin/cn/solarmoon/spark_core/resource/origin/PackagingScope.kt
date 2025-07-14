package cn.solarmoon.spark_core.resource.origin

/**
 * 定义依赖在打包时的行为。
 */
enum class PackagingScope {
    /**
     * 将依赖的实体文件包含在包中。
     */
    INCLUDE,

    /**
     * 只在新模块的元数据中记录依赖信息，不打包其实体文件。
     */
    REFERENCE_ONLY
} 