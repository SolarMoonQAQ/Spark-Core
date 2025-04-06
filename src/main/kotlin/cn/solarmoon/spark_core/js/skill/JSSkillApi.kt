package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.skill.SkillManager
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

object JSSkillApi: JSApi {

    override val id: String = "skill"
    override val valueCache: MutableMap<String, String> = mutableMapOf()

    private val preLoads = mutableListOf<Pair<JSSkillTypeBuilder, () -> Unit>>()

    @HostAccess.Export
    fun create(id: String, consumer: Value) = create(id, null, consumer)

    @HostAccess.Export
    fun createBy(id: String, by: String, consumer: Value) = create(id, by, consumer)

    fun create(id: String, by: String? = null, consumer: Value) {
        val builder = JSSkillTypeBuilder()
        builder.id = ResourceLocation.parse(id)
        consumer.execute(builder)
        preLoads.add(
            builder to { if (by == null) builder.build() else builder.buildBy(SkillManager[ResourceLocation.parse(by)] ?: throw NullPointerException("父技能 $by 无法被继承，因为此技能尚未注册")) }
        )
    }

    override fun onLoad() {
        preLoads.sortByDescending { it.first.priority }
        preLoads.forEach { it.second.invoke() }
        preLoads.clear()
    }

    override fun onRegister(engine: GraalJSScriptEngine) {
        engine.put("Skill", this)
    }

    override fun onReload() {
        val r = SkillManager.filter { it.value.fromJS }.toList()
        r.forEach {
            SkillManager.remove(it.first)
        }
    }

}