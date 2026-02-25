package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.event.MolangRegisterEvent
import net.neoforged.neoforge.common.NeoForge
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import java.util.concurrent.ConcurrentHashMap
private val extraBindings = ThreadLocal.withInitial {
    // 发布注册事件，让外部可订阅
    val contexts: MutableMap<String, IMolangContext> = LinkedHashMap()
    val queryContext = QueryContext()
    val variableContext = VariableContext()
    contexts["query"] = queryContext
    contexts["q"] = queryContext
    contexts["variable"] = variableContext
    contexts["v"] = variableContext
    NeoForge.EVENT_BUS.post(MolangRegisterEvent(contexts))
    contexts
}
private val context = ThreadLocal.withInitial {
    val ctx = Context.newBuilder("js")
        .allowHostAccess(HostAccess.EXPLICIT)
        .build()
    // 基本的数学函数
    ctx.getBindings("js").putMember("math", MathContext())
    // 额外的上下文
    for ((name, context) in getMolangExtraBindings()) {
        ctx.getBindings("js").putMember(name, context)
    }
    ctx
}

fun getMolangJSContext() = context.get()

fun getMolangExtraBindings() = extraBindings.get()

fun Context.getJSBindings() = getBindings("js")


object MolangCache {
    private val cache = ConcurrentHashMap<String, Source>()

    fun getOrCompile(code: String): Source {
        return cache.computeIfAbsent(code) {
            Source.newBuilder("js", it, "molang").build()
        }
    }
}