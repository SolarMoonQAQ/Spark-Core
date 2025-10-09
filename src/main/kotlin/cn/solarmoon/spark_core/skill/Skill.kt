package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.payload.SkillPayload
import cn.solarmoon.spark_core.util.InlineEventConsumer
import cn.solarmoon.spark_core.util.InlineEventHandler
import cn.solarmoon.spark_core.util.triggerEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.activeStates
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import kotlin.reflect.KClass

open class Skill: InlineEventHandler<SkillEvent> {

    companion object {
        val LOGGER = SparkCore.logger("技能系统")
    }

    override val eventHandlers: MutableMap<KClass<out SkillEvent>, MutableList<InlineEventConsumer<out SkillEvent>>> = mutableMapOf()

    private object StartEvent: Event
    private object EndEvent: Event
    private val lifecycleState = createStdLibStateMachine {
        val idle = initialState("idle")
        val active = state("active")
        val end = state("end")

        idle.apply {
            transition<StartEvent> {
                targetState = active
            }
        }
        active.apply {
            onEntry {
                triggerEvent(SkillEvent.Start)
            }
            transition<EndEvent> {
                targetState = end
            }
        }
        end.apply {
            onEntry {
                if (id < 0) holder.predictedSkills.remove(id)
                else holder.allSkills.remove(id)
                targetPool.clear()
            }
        }
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

    val isActivated get() = lifecycleState.activeStates().first().name == "active"

    fun init(id: Int, type: SkillType<*>, holder: SkillHost, level: Level) = apply {
        this.id = id
        this.type = type
        this.holder = holder
        this.level = level

        config.init(this)
        triggerEvent(SkillEvent.Init)
        targetPool.init(this)
    }

    fun activate() {
        try {
            if (type.isIndependent) {
                // 结束所有相同类型的技能，保证独立性质的技能同一时间只能存在一个
                holder.allSkills.values.filter { it.type.registryKey == type.registryKey && it.id != id }.forEach { it.end() }
            }
            lifecycleState.processEventBlocking(StartEvent)
        } catch (e: Exception) {
            LOGGER.error("技能 ${type.registryKey} 启动时发生故障: ", e)
            end()
        }
    }

    fun update() {
        triggerEvent(SkillEvent.Update)
    }

    fun end(): Boolean {
        if (triggerEvent(SkillEvent.End(true)).canEnd) {
            lifecycleState.processEventBlocking(EndEvent)
            return true
        }
        return false
    }

    fun endOnClient() {
        PacketDistributor.sendToServer(SkillPayload(this, CompoundTag().apply { putBoolean("endS", true) }))
    }

    fun endOnServer() {
        if (end()) PacketDistributor.sendToAllPlayers(SkillPayload(this, CompoundTag().apply { putBoolean("endC", true) }))
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