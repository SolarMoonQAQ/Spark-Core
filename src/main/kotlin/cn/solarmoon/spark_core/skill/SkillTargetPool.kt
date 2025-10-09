package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.skill.payload.SkillPayload
import cn.solarmoon.spark_core.util.onEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor

class SkillTargetPool() {

    private lateinit var skill: Skill
    private val targets = linkedSetOf<Entity>()

    fun init(skill: Skill) {
        this.skill = skill
        skill.onEvent<SkillEvent.Sync> { event ->
            val level = skill.level
            val data = event.data
            if (data.getBoolean("#AddTarget")) {
                addTarget(level.getEntity(data.getInt("#Target"))!!)
            }

            if (data.getBoolean("#RemoveTarget")) {
                removeTarget(level.getEntity(data.getInt("#Target"))!!)
            }
        }
    }

    fun addTarget(entity: Entity, sync: Boolean = false) {
        targets.add(entity)
        SkillManager.registerSkillTarget(entity, skill)
        if (sync) {
            PacketDistributor.sendToAllPlayers(SkillPayload(skill, CompoundTag().apply {
                putBoolean("#AddTarget", true)
                putInt("#Target", entity.id)
            }))
        }
    }

    fun removeTarget(entity: Entity, sync: Boolean = false) {
        targets.remove(entity)
        SkillManager.unregisterSkillTarget(entity, skill)
        if (sync) {
            PacketDistributor.sendToAllPlayers(SkillPayload(skill, CompoundTag().apply {
                putBoolean("#RemoveTarget", true)
                putInt("#Target", entity.id)
            }))
        }
    }

    fun getTargets() = targets.toList()

    fun clear() {
        targets.forEach { SkillManager.unregisterSkillTarget(it, skill) }
        targets.clear()
    }

}