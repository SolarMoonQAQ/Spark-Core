package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.component.SkillComponent
import cn.solarmoon.spark_core.skill.payload.SkillPayload
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function

abstract class Skill {

    var id: Int = 0
    lateinit var type: SkillType<*>
    lateinit var holder: SkillHost
    lateinit var level: Level

    var isActive = false
        private set
    var isInitialized = false
        private set
    val components = ConcurrentLinkedQueue<SkillComponent>()
    val timeline = SkillTimeLine(this)
    private val targets = linkedSetOf<Entity>()
    val physicsBodies = linkedSetOf<PhysicsCollisionObject>()

    fun activate() {
        if (!isActive) {
            timeline.runTime = 0
            isActive = true
            if (!onActive()) end()
            else isInitialized = true
        } else {
            SparkCore.LOGGER.warn("技能正在释放中，无法重复启用，请等待该技能释放完毕，或先结束该技能。")
        }
    }

    fun update() {
        if (isActive) {
            timeline.runTime++
            onUpdate()
            components.removeIf {
                it.tick()
                it.isRemoved
            }
        }
    }

    fun physicsTick() {
        onPhysicsTick()
        components.forEach { it.physicsTick() }
    }

    fun end() {
        if (isActive && shouldEnd()) {
            isActive = false
            if (id < 0) holder.predictedSkills.remove(id)
            else holder.allSkills.remove(id)
            if (isInitialized) onEnd()
            components.forEach { it.detach() }
            components.clear()
            targets.forEach { SkillManager.unregisterSkillTarget(it, this) }
            targets.clear()
        }
    }

    fun hurt(event: LivingIncomingDamageEvent) {
        onHurt(event)
        components.forEach { it.onHurt(event) }
    }

    fun targetHurt(event: LivingIncomingDamageEvent) {
        onTargetHurt(event)
        components.forEach { it.onTargetHurt(event) }
    }

    fun damage(event: LivingDamageEvent) {
        onDamage(event)
        components.forEach { it.onDamage(event) }
    }

    fun targetDamage(event: LivingDamageEvent) {
        onTargetDamage(event)
        components.forEach { it.onTargetDamage(event) }
    }

    fun knockBack(event: LivingKnockBackEvent) {
        onKnockBack(event)
        components.forEach { it.onKnockBack(event) }
    }

    fun targetKnockBack(event: LivingKnockBackEvent) {
        onTargetKnockBack(event)
        components.forEach { it.onTargetKnockBack(event) }
    }

    open fun new(id: Int, type: SkillType<*>, host: SkillHost, level: Level): Skill {
        return codec.codec().decode(JsonOps.INSTANCE, CODEC.encodeStart(JsonOps.INSTANCE, this).orThrow).orThrow.first.apply {
            this.id = id
            this.type = type
            this.holder = host
            this.level = level
        }
    }

    fun endOnClient() {
        PacketDistributor.sendToServer(SkillPayload(this, CompoundTag().apply { putBoolean("endS", true) }))
    }

    fun endOnServer() {
        end()
        PacketDistributor.sendToAllPlayers(SkillPayload(this, CompoundTag().apply { putBoolean("endC", true) }))
    }

    fun sync(data: CompoundTag, context: IPayloadContext) {
        if (data.getBoolean("endS")) {
            endOnServer()
        } else if (data.getBoolean("endC")) {
            end()
        } else {
            onSync(data, context)
        }
    }

    abstract val codec: MapCodec<out Skill>

    protected open fun onActive(): Boolean = true

    protected open fun onUpdate() {}

    protected open fun onEnd() {}

    open fun shouldEnd(): Boolean = true

    protected open fun onEvent(event: Event) {}

    protected open fun onPhysicsTick() {}

    protected open fun onSync(data: CompoundTag, context: IPayloadContext) {}

    protected open fun onHurt(event: LivingIncomingDamageEvent) {}

    protected open fun onTargetHurt(event: LivingIncomingDamageEvent) {}

    protected open fun onDamage(event: LivingDamageEvent) {}

    protected open fun onTargetDamage(event: LivingDamageEvent) {}

    protected open fun onKnockBack(event: LivingKnockBackEvent) {}

    protected open fun onTargetKnockBack(event: LivingKnockBackEvent) {}

    fun addTarget(entity: Entity) {
        targets.add(entity)
        SkillManager.registerSkillTarget(entity, this)
    }

    fun removeTarget(entity: Entity) {
        targets.remove(entity)
        SkillManager.unregisterSkillTarget(entity, this)
    }

    fun getTargets() = targets.toList()

    fun handleEvent(event: Event) {
        onEvent(event)
        components.forEach {
            it.handleEvent(event)
        }
    }

    // 实用方法
    protected fun <T: SkillComponent> List<T>.attachAll(provider: (SkillComponent) -> Unit = {}) = forEach { provider.invoke(it); it.attach(this@Skill) }

    companion object {
        val CODEC = SparkRegistries.SKILL_CODEC.byNameCodec()
            .dispatch(
                Skill::codec,
                Function.identity()
            )
    }

}