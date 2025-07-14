package cn.solarmoon.spark_core.js.resource

import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.JSComponent

/**
 * JavaScript资源路径API
 * 
 * 为JavaScript环境提供资源路径构建服务
 * 与JSEntity、JSSkillApi等保持一致的API风格
 */
object JSResourcePathApi : JSApi, JSComponent() {
    
    override val id: String = "resource_path"
    
    override fun onLoad() {
        // API加载完成后的初始化逻辑
    }
    
    override fun onReload() {
        // API重载时的清理和重新初始化逻辑
    }
}
