package cn.solarmoon.spark_core.js.ik

// import cn.solarmoon.spark_core.js.put // 根据差异逻辑移除（put 在 onRegister 中使用）
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.call
import net.minecraft.resources.ResourceLocation
import org.mozilla.javascript.Function
import org.slf4j.LoggerFactory

object JSIKApi: JSApi,JSComponent() {
    override val id: String = "ik" // 暴露给 JS 的名称（例如，IK.create(...)）
    override val valueCache: MutableMap<String, String> = mutableMapOf()

    // 使用 SLF4J 日志
    private val logger = LoggerFactory.getLogger(JSIKApi::class.java)

    // 存储构建器直到 onLoad 被调用
    // 假设 JSIKComponentTypeBuilder 存在，并且具有 'id'、'priority' 和 'build()' 方法
    private val pendingRegistrations = mutableListOf<Pair<JSIKComponentTypeBuilder, () -> Unit>>()


    fun create(idStr: String, configureFunc: Function) {
        val builder = JSIKComponentTypeBuilder() // 假设这个类存在
        // 存储配置逻辑，以便稍后在 onLoad 期间运行
        pendingRegistrations.add(builder to {
            try {
                // 首先设置 ID，因为 configureFunc 可能隐式需要它
                builder.id = ResourceLocation.parse(idStr)
                // 调用 JS 函数，传递构建器进行配置
                configureFunc.call(engine, builder) // 传递引擎上下文和构建器
                // 配置完成，注册将在 onLoad 中进行
                // builder.buildAndRegister() // 此处移除了注册调用
            } catch (e: Exception) {
                logger.error("配置 TypedIKComponent '$idStr' 时发生错误：", e) // 使用日志记录器
            }
        })
        logger.debug("JS 已排队 TypedIKComponent 定义：$idStr") // 使用日志记录器
    }

    override fun onLoad() {
        logger.info("正在处理 ${pendingRegistrations.size} 个待定的 JS TypedIKComponent 注册...") // 使用日志记录器
        // 如果需要，按优先级排序，然后注册
        pendingRegistrations.sortByDescending { it.first.priority } // 假设 'priority' 存在
        // pendingRegistrations.forEach { it.second.invoke() } // 此处移除了直接调用
        pendingRegistrations.forEach { (builder, configureAction) ->
            try {
                configureAction.invoke() // 确保配置已应用
                val typeToRegister: TypedIKComponent? = builder.build() // 构建类型实例，假设 build() 存在并返回 TypedIKComponent?
                if (typeToRegister != null) {
                    // 通过 SparkCore.REGISTER 使用 DeferredRegister 模式
                    // 假设 SparkCore.REGISTER.ikComponentType() 返回一个 DeferredRegister<TypedIKComponent>
                    // 并且它有一个接受路径和供应器的构建方法
                    SparkCore.REGISTER.ikComponentType().build(typeToRegister.id.path) { typeToRegister }
                    logger.info("JS 提交用于注册的 TypedIKComponent：${typeToRegister.id}") // 使用日志记录器
                } else {
                     logger.error("构建 TypedIKComponent 失败，来自 JS 定义：${builder.id}")
                }
            } catch (e: Exception) {
                 logger.error("处理 JS TypedIKComponent 注册时发生错误（针对 '${builder.id ?: "unknown"}'）：", e)
            }
        }
        pendingRegistrations.clear()
        logger.info("已完成 JS TypedIKComponent 注册处理。") // 使用日志记录器
    }

    override fun onReload() {
        // TODO: 决定如何在重新加载时处理 JS 定义的类型。
        // 选项 1: 清除它们（需要跟踪哪些是 JS 定义的）。
        // 选项 2: 保留它们（如果脚本重新定义，可能会导致重复）。
        // 选项 3: 实现一个正确的重新加载机制。
        logger.warn("IK 组件类型从 JS 重新加载尚未完全实现。") // 使用日志记录器
        // 目前，清除那些没有加载的待处理项？
        pendingRegistrations.clear()
    }
}
