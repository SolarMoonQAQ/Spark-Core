package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.skill.SkillManager
import net.minecraft.resources.ResourceLocation
import org.mozilla.javascript.Function

object JSSkillApi: JSApi, JSComponent() {

    override val id: String = "skill"
    // 移除本地缓存，统一使用动态注册表数据源

    private val preLoads = mutableListOf<Pair<JSSkillTypeBuilder, () -> Unit>>()

    /**
     * 当前正在处理的脚本文件名
     * 用于在构建技能时设置脚本来源信息
     */
    var currentFileName: String? = null

    fun create(id: String, consumer: Function) = create(id, null, consumer)

    fun createBy(id: String, by: String, consumer: Function) = create(id, by, consumer)

    fun create(id: String, by: String? = null, consumer: Function) {
        val builder = JSSkillTypeBuilder(engine)
        // 为了调试，保存ID字符串（不设置id属性，因为那是lateinit的）
        builder.debugIdString = id
        preLoads.add(
            builder to {
                builder.id = ResourceLocation.parse(id)
                // 设置当前文件名到builder
                builder.currentFileName = currentFileName
                consumer.call(engine, builder)
                if (by == null) builder.build() else builder.buildBy(SkillManager[ResourceLocation.parse(by)] ?: throw NullPointerException("父技能 $by 无法被继承，因为此技能尚未注册"))
            }
        )
    }

    override fun onLoad() {
        SparkCore.LOGGER.debug("JSSkillApi onLoad开始 (线程: {}, preLoads数量: {})", 
            Thread.currentThread().name, preLoads.size)
        
        // 记录排序前的顺序
        preLoads.forEachIndexed { index, (builder, _) ->
            SparkCore.LOGGER.debug("  排序前[{}]: {} (优先级: {})", index, builder.debugIdString ?: "未知ID", builder.priority)
        }
        
        preLoads.sortByDescending { it.first.priority }
        
        // 记录排序后的顺序
        preLoads.forEachIndexed { index, (builder, _) ->
            SparkCore.LOGGER.debug("  排序后[{}]: {} (优先级: {})", index, builder.debugIdString ?: "未知ID", builder.priority)
        }
        
        preLoads.forEach { it.second.invoke() }
        preLoads.clear()
        
        // 打印最终的技能注册顺序
        SkillManager.debugPrintSkillOrder("JSSkillApi onLoad完成")
    }

    /**
     * 精确卸载指定脚本文件创建的技能
     * @param fileName 脚本文件名（不包含扩展名）
     */
    fun unloadScript(fileName: String) {
        val toRemove = SkillManager.filter {
            it.value.fromScript &&
            it.value.scriptSource?.apiId == id &&
            it.value.scriptSource?.fileName == fileName
        }.toList()

        toRemove.forEach {
            SkillManager.remove(it.first)
            SparkCore.LOGGER.debug("移除技能: ${it.first} (来源脚本: $fileName)")
        }

        if (toRemove.isNotEmpty()) {
            SparkCore.LOGGER.info("已卸载脚本 $fileName 创建的 ${toRemove.size} 个技能")
        }
    }

    override fun onReload() {
        // 清除所有由此API创建的JS技能
        val r = SkillManager.filter {
            it.value.fromScript &&
            it.value.scriptSource?.apiId == id
        }.toList()
        r.forEach {
            SkillManager.remove(it.first)
        }
        SparkCore.LOGGER.debug("onReload: 清除了 ${r.size} 个技能")
    }

}