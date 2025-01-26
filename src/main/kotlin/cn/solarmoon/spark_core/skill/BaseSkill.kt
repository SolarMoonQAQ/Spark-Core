package cn.solarmoon.spark_core.skill

import cn.solarmoon.spirit_of_fight.feature.fight_skill.skill.PersistentSkillComponent
import cn.solarmoon.spirit_of_fight.feature.fight_skill.skill.SkillComponent
import cn.solarmoon.spirit_of_fight.feature.fight_skill.skill.TriggeredSkillComponent

abstract class BaseSkill<T>(
    override val holder: T
): Skill<T> {

    override val name: String get() = this::class.java.simpleName

    protected var active = false
    protected var _runTime = 0
    override val runTime: Int
        get() = _runTime
    override val components: MutableList<SkillComponent> = mutableListOf()

    override fun activate() {
        if (!active) {
            active = true
            onActivate()
        }
    }

    override fun update() {
        if (active) {
            _runTime++
            onUpdate()
        }
    }

    override fun end() {
        if (active) {
            _runTime = 0
            active = false
            onEnd()
        }
    }

    override fun isActive(): Boolean = active

    protected open fun onActivate() {
        components.forEach {
            if (it is TriggeredSkillComponent) it.start()
        }
    }

    protected open fun onUpdate() {
        components.forEach {
            if (it is PersistentSkillComponent) it.tick()
        }
    }

    protected open fun onEnd() {
        components.forEach {
            if (it is TriggeredSkillComponent) it.stop()
        }
    }

}