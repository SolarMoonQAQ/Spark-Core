package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.payload.SkillPayload
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.reflect.KClass

open class Skill {

    companion object {
        val LOGGER = SparkCore.logger("技能系统")
    }

    var id: Int = 0
        internal set
    lateinit var type: SkillType<*>
        private set
    lateinit var holder: SkillHost
        private set
    lateinit var level: Level
        private set

    open val config: SkillConfig = DefaultSkillConfig()
    val targetPool = SkillTargetPool()

    var transitionGuard: (SkillState) -> Boolean = { true }

    lateinit var currentState: SkillState
        private set

    var initialState = SkillState.PREPARE
    var endState = SkillState.END
    open val states = linkedMapOf<String, SkillState>()

    val eventHandlers = mutableMapOf<KClass<out SkillEvent>, MutableList<Skill.(SkillEvent) -> Unit>>()

    var isActivated = false
        private set

    fun addState(state: SkillState) {
        states[state.name] = state
    }

    fun init(id: Int, type: SkillType<*>, holder: SkillHost, level: Level) = apply {
        this.id = id
        this.type = type
        this.holder = holder
        this.level = level

        config.init(this)
        triggerEvent(SkillEvent.Init)
        targetPool.init(this)
    }

    inline fun <reified T : SkillEvent> onEvent(crossinline handler: Skill.(T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        eventHandlers.getOrPut(T::class) { mutableListOf() }.add { handler.invoke(this, it as T) }
    }

    fun triggerEvent(event: SkillEvent): Boolean {
        var result = false
        eventHandlers[event::class]?.forEach { it(event); result = true }
        return result
    }

    fun transitionTo(stateName: String): Boolean {
        val nextState = states[stateName] ?: error("技能 ${type.registryKey} 的内部状态 $stateName 未找到！")
        return transitionTo(nextState)
    }

    fun transitionTo(nextState: SkillState): Boolean {
        // 不允许重复进入同一个状态
        if (::currentState.isInitialized && currentState == nextState) return false
        // 检查状态转换条件
        if (!transitionGuard(nextState)) return false

        var first = false
        if (::currentState.isInitialized) {
            currentState.onExit(this)
            triggerEvent(SkillEvent.State.Exit(currentState))
        } else {
            isActivated = true
            first = true
        }

        currentState = nextState
        currentState.tickCount = 0
        currentState.times++
        currentState.onEnter(this)
        if (first) triggerEvent(SkillEvent.Start)
        triggerEvent(SkillEvent.State.Enter(nextState))

        if (currentState == endState) cleanup()
        return true
    }

    fun activate() {
        try {
            if (type.isIndependent) {
                // 结束所有相同类型的技能，保证独立性质的技能同一时间只能存在一个
                holder.allSkills.values.filter { it.type.registryKey == type.registryKey && it.id != id }.forEach { it.end() }
            }
            transitionTo(initialState)
        } catch (e: Exception) {
            LOGGER.error("技能 ${type.registryKey} 启动时发生故障: ", e)
            end()
        }
    }

    fun update() {
        if (::currentState.isInitialized) {
            currentState.onUpdate(this)
            currentState.tickCount++
            triggerEvent(SkillEvent.State.Update(currentState))
        }
    }

    fun end() = transitionTo(endState)

    fun endOnClient() {
        PacketDistributor.sendToServer(SkillPayload(this, CompoundTag().apply { putBoolean("endS", true) }))
    }

    fun endOnServer() {
        if (end()) PacketDistributor.sendToAllPlayers(SkillPayload(this, CompoundTag().apply { putBoolean("endC", true) }))
    }

    private fun cleanup() {
        triggerEvent(SkillEvent.End)
        isActivated = false
        if (id < 0) holder.predictedSkills.remove(id)
        else holder.allSkills.remove(id)
        targetPool.clear()
    }

    internal fun sync(data: CompoundTag, context: IPayloadContext) {
        if (data.getBoolean("endS")) {
            endOnServer()
        } else if (data.getBoolean("endC")) {
            end()
        } else {
            triggerEvent(SkillEvent.Sync(data, context))
        }
    }

}