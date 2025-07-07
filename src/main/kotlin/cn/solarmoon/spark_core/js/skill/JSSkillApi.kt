package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.put
import cn.solarmoon.spark_core.skill.SkillManager
import net.minecraft.resources.ResourceLocation
import org.mozilla.javascript.Function

object JSSkillApi: JSApi, JSComponent() {

    override val id: String = "skill"
    // 移除本地缓存，统一使用动态注册表数据源

    private val preLoads = mutableListOf<Pair<JSSkillTypeBuilder, () -> Unit>>()

    fun create(id: String, consumer: Function) = create(id, null, consumer)

    fun createBy(id: String, by: String, consumer: Function) = create(id, by, consumer)

    fun create(id: String, by: String? = null, consumer: Function) {
        val builder = JSSkillTypeBuilder(engine)
        preLoads.add(
            builder to {
                builder.id = ResourceLocation.parse(id)
                consumer.call(engine, builder)
                if (by == null) builder.build() else builder.buildBy(SkillManager[ResourceLocation.parse(by)] ?: throw NullPointerException("父技能 $by 无法被继承，因为此技能尚未注册"))
            }
        )
    }

    override fun onLoad() {
        preLoads.sortByDescending { it.first.priority }
        preLoads.forEach { it.second.invoke() }
        preLoads.clear()
    }

    override fun onReload() {
        val r = SkillManager.filter { it.value.fromJS }.toList()
        r.forEach {
            SkillManager.remove(it.first)
        }
    }

}