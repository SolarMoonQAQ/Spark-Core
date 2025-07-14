package cn.solarmoon.spark_core.resource.module

import cn.solarmoon.spark_core.resource.origin.OModuleInfo
import java.nio.file.Path

/**
 * 模块感知处理器接口
 * 为支持模块系统的处理器提供声明周期钩子
 */
interface IModuleAwareHandler {
    /**
     * 当一个模块被注册时调用
     * @param module 模块的完整信息
     */
    fun onModuleRegistered(module: OModuleInfo)

    /**
     * 当一个模块被注销时调用
     * @param module 模块的完整信息
     */
    fun onModuleUnregistered(module: OModuleInfo)

    /**
     * 当所有模块都注册和初始化完毕后调用
     */
    fun onAllModulesLoaded()
}