package cn.solarmoon.spark_core.skill

abstract class BaseSkill<T>(
    override val holder: T,
    override val skillType: SkillType<T, out Skill<T>>
): Skill<T> {

    protected var active = false

    override fun activate() {
        if (!active) {
            active = true
            onActivate()
        }
    }

    override fun update() {
        if (active) {
            onUpdate()
        }
    }

    override fun end() {
        if (active) {
            active = false
            onEnd()
        }
    }

    override fun isActive(): Boolean = active

    protected abstract fun onActivate()

    protected abstract fun onUpdate()

    protected abstract fun onEnd()

}