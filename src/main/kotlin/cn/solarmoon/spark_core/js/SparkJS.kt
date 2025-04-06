package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.sync.JSPayload
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.skillType
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModLoader
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.util.LoaderException
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.registration.NetworkRegistry
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import java.io.File

object SparkJS {

    lateinit var allApi: Map<String, JSApi>
        private set

    val engine = GraalJSScriptEngine.create(
        null,
        Context.newBuilder("js")
            .allowHostAccess(
                HostAccess.newBuilder()
                    .allowPublicAccess(true)
                    .allowAccessAnnotatedBy(HostAccess.Export::class.java)
                    .build()
            )
            .allowHostClassLookup { true }
    )

    /**
     * 用于注册[JSApi]到双端
     */
    fun register() {
        val regs = mutableSetOf<JSApi>()
        ModLoader.postEvent(SparkJSRegisterEvent(engine, regs))
        allApi = buildMap { regs.forEach { put(it.id, it) } }
        allApi.values.forEach {
            it.onRegister(engine)
        }
        loadAll()
    }

    fun eval(script: String, context: String = "") {
        runCatching { engine.eval(script) }.getOrElse { throw LoaderException("Js脚本加载失败: $context", it) }
    }

    /**
     * 动态加载对应api路径下的所有脚本
     */
    fun load(api: JSApi) {
        validateApi(api)
        val gameDir = FMLPaths.GAMEDIR.get().toFile()
        val file = File(gameDir, "sparkcore/script/${api.id}")
        file.listFiles { it.extension == "js" }?.forEach {
            val value = it.readText()
            eval(value, it.path)
            api.valueCache[it.name] = value
            SparkCore.LOGGER.info("已加载脚本：模块：${api.id} 文件：${it.name}")
        }
        api.onLoad()
    }

    fun loadAll() = allApi.forEach { load(it.value) }

    /**
     * 动态重载对应api路径下的所有脚本并发送重载结果到客户端，客户端会进行一次[JSApi.onReload]
     */
    fun reload(api: JSApi) {
        validateApi(api)
        api.onReload()
        load(api)
        PacketDistributor.sendToAllPlayers(JSPayload(mapOf(api.id to api.valueCache), true))
    }

    fun reloadAll() = allApi.forEach { reload(it.value) }

    fun validateApi(api: String) {
        if (!allApi.contains(api)) throw NullPointerException("JS API $api 尚未注册！")
    }

    fun validateApi(api: JSApi) = validateApi(api.id)

}