package cn.solarmoon.spark_core.resource.origin

/**
 * 模块描述器
 * 对应 spark_module.json 配置文件的数据结构
 */
data class ModuleDescriptor(
    /** 模块唯一标识符，必须全局唯一 */
    val id: String,
    
    /** 模块版本，推荐使用语义化版本（SemVer） */
    val version: String,
    
    /** 模块简短描述 */
    val description: String? = null,
    
    /** 模块作者或组织 */
    val author: String? = null,
    
    /** 强依赖列表，支持版本约束语法 */
    val depends: List<String> = emptyList(),
    
    /** 软依赖/推荐依赖列表 */
    val recommends: List<String> = emptyList(),
    
    /** 提供的虚拟特性列表 */
    val provides: List<String> = emptyList(),
    
    /** 冲突模块列表 */
    val conflicts: List<String> = emptyList(),
    
    /** 是否为付费内容（为未来功能预留） */
    val paid: Boolean = false
) {
    companion object {
        /** 模块描述文件名 */
        const val DESCRIPTOR_FILE_NAME = "spark_module.json"
    }
}

