package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.world.entity.Entity

@LuaClass
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

    /**
     * 展开233！！！
     * 哇哦
     * @param consumer 这是对的
     */
    fun onActive(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.Active> {
        consumer.pushValue()
        consumer.luaState.call(0, 0)
    }

    fun onActiveStart(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.ActiveStart> {
        consumer.pushValue()
        consumer.luaState.call(0, 0)
    }

    fun onEnd(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.End> {
        consumer.pushValue()
        consumer.luaState.call(0, 0)
    }

    fun onLocalInputUpdate(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.LocalInputUpdate> {
        consumer.pushValue()
        consumer.luaState.pushJavaObject(it.event)
        consumer.luaState.call(1, 0)
    }

    fun onTargetHurt(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetHurt> {
        consumer.pushValue()
        consumer.luaState.pushJavaObject(it.event)
        consumer.luaState.call(1, 0)
    }

    fun onTargetActualHurtPre(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetActualHurt.Pre> {
        consumer.pushValue()
        consumer.luaState.pushJavaObject(it.event)
        consumer.luaState.call(1, 0)
    }

    fun onTargetActualHurtPost(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetActualHurt.Post> {
        consumer.pushValue()
        consumer.luaState.pushJavaObject(it.event)
        consumer.luaState.call(1, 0)
    }

    fun onHurt(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.Hurt> {
        consumer.pushValue()
        consumer.luaState.pushJavaObject(it.event)
        consumer.luaState.call(1, 0)
    }

    fun onTargetKnockBack(consumer: LuaValueProxy) = skill.onEvent<SkillEvent.TargetKnockBack> {
        consumer.pushValue()
        consumer.luaState.pushJavaObject(it.event)
        consumer.luaState.call(1, 0)
    }

    /**
     * 哈哈
     * @return 来了
     */
    fun getLocation() = skill.type.registryKey

}