package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.llamalad7.mixinextras.utils.Blackboard
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Function
import kotlin.reflect.KClass

abstract class Skill {

    var id: Int = 0
    lateinit var type: SkillType<*>
    lateinit var holder: SkillHost
    lateinit var level: Level

    var isActive = false
        private set
    var runTime: Int = 0
        private set
    var eventHandlers = mutableMapOf<KClass<out SkillEvent>, MutableList<(SkillEvent) -> Unit>>()
        private set
    val blackBoard = BlackBoard()
    val components = linkedSetOf<SkillComponent>()

    fun activate() {
        if (!isActive) {
            runTime = 0
            isActive = true
            onActive()
            triggerEvent(SkillEvent.Active)
        } else {
            SparkCore.LOGGER.warn("技能正在释放中，无法重复启用，请等待该技能释放完毕，或先结束该技能。")
        }
    }

    fun update() {
        if (isActive) {
            runTime++
            onUpdate()
            triggerEvent(SkillEvent.Update)
        }
    }

    fun end() {
        if (isActive) {
            isActive = false
            if (id < 0) holder.predictedSkills.remove(id)
            else holder.allSkills.remove(id)
            onEnd()
            triggerEvent(SkillEvent.End)
            components.forEach { it.detach() }
        }
    }

    open fun new(id: Int, type: SkillType<*>, host: SkillHost, level: Level): Skill {
        return codec.codec().decode(JsonOps.INSTANCE, CODEC.encodeStart(JsonOps.INSTANCE, this).orThrow).orThrow.first.apply {
            this.id = id
            this.type = type
            this.holder = host
            this.level = level
            this.init()
        }
    }

    inline fun <reified T : SkillEvent> onEvent(crossinline handler: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        eventHandlers.getOrPut(T::class) { mutableListOf() }.add { handler.invoke(it as T) }
    }

    fun triggerEvent(event: SkillEvent) {
        eventHandlers[event::class]?.forEach { it(event) }
    }

    abstract val codec: MapCodec<out Skill>

    protected open fun init() {}

    protected open fun onActive() {}

    protected open fun onUpdate() {}

    protected open fun onEnd() {}

    protected open fun onEvent(event: Event) {}

    open fun sync(data: CompoundTag, context: IPayloadContext) {}

    fun handleEvent(event: Event) {
        components.forEach {
            it.handleEvent(event)
        }
        onEvent(event)
    }

    companion object {
        val CODEC = SparkRegistries.SKILL_CODEC.byNameCodec()
            .dispatch(
                Skill::codec,
                Function.identity()
            )
    }

}