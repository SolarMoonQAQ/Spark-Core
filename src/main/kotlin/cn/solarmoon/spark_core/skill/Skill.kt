package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.extension.JSSkill
import cn.solarmoon.spark_core.skill.payload.SkillPayload
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.reflect.KClass

open class Skill {

    var id: Int = 0
        internal set
    lateinit var type: SkillType<*>
        private set
    lateinit var holder: SkillHost
        private set
    lateinit var level: Level
        private set

    val config = SkillConfig(this)
    val targetPool = SkillTargetPool(this)

    var canTransitionTo: (SkillPhase) -> Boolean = { true }

    var phase = SkillPhase.IDLE
        private set(value) {
            field = value
            when(value) {
                SkillPhase.WINDUP -> {
                    if (!triggerEvent(SkillEvent.WindupStart)) transitionTo(SkillPhase.ACTIVE)
                }
                SkillPhase.ACTIVE -> {
                    triggerEvent(SkillEvent.ActiveStart)
                }
                SkillPhase.RECOVERY -> {
                    if (!triggerEvent(SkillEvent.RecoveryStart)) transitionTo(SkillPhase.END)
                }
                SkillPhase.END -> {
                    if (id < 0) holder.predictedSkills.remove(id)
                    else holder.allSkills.remove(id)
                    targetPool.clear()
                    triggerEvent(SkillEvent.End)
                }
                else -> {}
            }
        }

    var tickCount = 0
        private set
    var windupTickCount = 0
        private set
    var activeTickCount = 0
        private set
    var recoveryTickCount = 0
        private set

    val eventHandlers = mutableMapOf<KClass<out SkillEvent>, MutableList<Skill.(SkillEvent) -> Unit>>()
    private val initHandlers = mutableListOf<Skill.() -> Unit>()
    private val syncHandlers = mutableListOf<Skill.(CompoundTag, IPayloadContext) -> Unit>()

    val isActivated get() = phase !in arrayOf(SkillPhase.IDLE, SkillPhase.END)

    fun init(id: Int, type: SkillType<*>, holder: SkillHost, level: Level) = apply {
        this.id = id
        this.type = type
        this.holder = holder
        this.level = level
        initHandlers.forEach { it.invoke(this) }
        config.init()
        targetPool.init()
    }

    fun init(handler: Skill.() -> Unit) {
        initHandlers.add(handler)
    }

    fun sync(handler: Skill.(CompoundTag, IPayloadContext) -> Unit) {
        syncHandlers.add(handler)
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

    fun transitionTo(phase: SkillPhase): Boolean {
        return if (this.phase != phase && canTransitionTo(phase)) {
            this.phase = phase
            true
        } else false
    }

    fun activate() {
        if (type.isIndependent) {
            holder.allSkills.values.filter { it.type.registryKey == type.registryKey && it.id != id }.forEach { it.end() }
        }

        when(phase) {
            SkillPhase.IDLE -> {
                if (canTransitionTo(SkillPhase.WINDUP)) {
                    transitionTo(SkillPhase.WINDUP)
                }
            }
            else -> {
                SparkCore.LOGGER.warn("技能已经触发，请创建新的技能实例并考虑手动结束当前技能以触发新的技能。")
            }
        }
    }

    fun update() {
        tickCount++
        when(phase) {
            SkillPhase.WINDUP -> {
                windupTickCount++
                triggerEvent(SkillEvent.Windup)
            }
            SkillPhase.ACTIVE -> {
                activeTickCount++
                triggerEvent(SkillEvent.Active)
            }
            SkillPhase.RECOVERY -> {
                recoveryTickCount++
                triggerEvent(SkillEvent.Recovery)
            }
            else -> throw IllegalStateException("技能在错误的阶段更新")
        }
    }

    fun end() = transitionTo(SkillPhase.END)

    fun endOnClient() {
        PacketDistributor.sendToServer(SkillPayload(this, CompoundTag().apply { putBoolean("endS", true) }))
    }

    fun endOnServer() {
        if (end()) PacketDistributor.sendToAllPlayers(SkillPayload(this, CompoundTag().apply { putBoolean("endC", true) }))
    }

    fun sync(data: CompoundTag, context: IPayloadContext) {
        if (data.getBoolean("endS")) {
            endOnServer()
        } else if (data.getBoolean("endC")) {
            end()
        } else {
            syncHandlers.forEach { it.invoke(this, data, context) }
        }
    }

}