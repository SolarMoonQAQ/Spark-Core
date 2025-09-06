package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.lua.execute
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.world.entity.Entity

@LuaClass("Skill")
interface LuaSkill {

    val skill get() = this as Skill

    fun addTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.addTarget(entity, true)
    }

    fun removeTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.removeTarget(entity, true)
    }

    fun initConfig(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.ConfigInit> {
        consumer.execute()
    }

    fun init(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.Init> {
        consumer.execute()
    }

    fun onActive(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.Active> {
        consumer.execute()
    }

    fun onActiveStart(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.ActiveStart> {
        consumer.execute()
    }

    fun onEnd(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.End> {
        consumer.execute()
    }

    fun onLocalInputUpdate(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.LocalInputUpdate> {
        consumer.execute(it.event)
    }

    fun onTargetHurt(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetHurt> {
        consumer.execute(it.event)
    }

    fun onTargetActualHurtPre(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetActualHurt.Pre> {
        consumer.execute(it.event)
    }

    fun onTargetActualHurtPost(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetActualHurt.Post> {
        consumer.execute(it.event)
    }

    fun onHurt(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.Hurt> {
        consumer.execute(it.event)
    }

    fun onTargetKnockBack(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetKnockBack> {
        consumer.execute(it.event)
    }

    fun getLocation() = skill.type.registryKey

}