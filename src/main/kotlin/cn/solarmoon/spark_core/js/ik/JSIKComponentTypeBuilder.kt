package cn.solarmoon.spark_core.js.ik

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import net.minecraft.resources.ResourceLocation

/**
 * 由 JS 脚本用于配置新的 TypedIKComponent 的构建器。
 */
class JSIKComponentTypeBuilder {
    // 可由 JS 设置的属性
    lateinit var id: ResourceLocation
    lateinit var chainName: String
    lateinit var startBoneName: String
    lateinit var endBoneName: String
    lateinit var bonePathNames: List<String> // 添加属性以支持显式路径
    var defaultTolerance: Float = 0.1f
    var defaultMaxIterations: Int = 15
    var priority: Int = 0 // 用于注册顺序（如果需要）

     fun setId(v: String) { id = ResourceLocation.parse(v) } // 允许通过字符串设置 ID
     fun setIKChainName(v: String) { chainName = v }
     fun setStartBone(v: String) { startBoneName = v }
     fun setEndBone(v: String) { endBoneName = v }
    // 用于 JS 设置显式骨骼路径的方法。接受 JS 数组并转换为 Kotlin List
     fun setBonePath(path: Array<String>) {
        bonePathNames = path.toList() // 存储为列表，如果为空则为 null
    }
     fun setTolerance(v: Float) { defaultTolerance = v }
     fun setMaxIterations(v: Int) { defaultMaxIterations = v }
     fun setIKPriority(v: Int) { priority = v }

    // 从收集的数据创建 TypedIKComponent 实例。注册在其他地方进行。
    fun build(): TypedIKComponent? {
        // 基本验证
        if (!::id.isInitialized || !::chainName.isInitialized || !::startBoneName.isInitialized || !::endBoneName.isInitialized) {
            SparkCore.LOGGER.error("JS IKComponentTypeBuilder: 缺少必填字段（id, chainName, startBoneName, endBoneName），潜在 ID 为 $id")
            return null
        }
        // 将所有收集到的数据传递给 TypedIKComponent 构造函数
        // 注意：假设关节约束是单独处理的，或者目前默认为 emptyMap()。
        // 如果需要在此处构建约束，请添加相关的属性和方法。
        val type = TypedIKComponent(
            id = id,
            chainName = chainName,
            startBoneName = startBoneName,
            endBoneName = endBoneName,
            bonePathNames = bonePathNames, // 如果已设置，则传递显式路径
            defaultTolerance = defaultTolerance,
            defaultMaxIterations = defaultMaxIterations
            // jointConstraints = buildConstraints() // 如果在此处构建约束的示例
        )
        return type
    }
}