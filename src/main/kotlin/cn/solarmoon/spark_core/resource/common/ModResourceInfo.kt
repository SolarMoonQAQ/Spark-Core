package cn.solarmoon.spark_core.resource.common

/**
 * 模组资源信息
 * 
 * 存储注册到SparkCore资源系统的模组信息，用于多mod资源管理
 * 
 * @param modId 模组ID，用作资源命名空间
 * @param modMainClass 模组主类，用于资源提取和类加载器访问
 */
data class ModResourceInfo(
    val modId: String,
    val modMainClass: Class<*>
) {
    override fun toString(): String {
        return "ModResourceInfo(modId='$modId', modMainClass=${modMainClass.simpleName})"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModResourceInfo) return false
        return modId == other.modId && modMainClass == other.modMainClass
    }
    
    override fun hashCode(): Int {
        return modId.hashCode() * 31 + modMainClass.hashCode()
    }
}
