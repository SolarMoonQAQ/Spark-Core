package cn.solarmoon.spark_core.skill

abstract class BaseSkill<T>(
    override val holder: T,
    override val skillType: SkillType<T, out Skill<T>>
): Skill<T> {

    protected var active = false
    protected var time = 0
    override val runTime: Int
        get() = time

    override fun activate() {
        if (!active) {
            active = true
            onActivate()
        }
    }

    override fun update() {
        if (active) {
            time++
            onUpdate()
        }
    }

    override fun end() {
        if (active) {
            time = 0
            active = false
            onEnd()
        }
    }

    override fun isActive(): Boolean = active

    protected abstract fun onActivate()

    protected abstract fun onUpdate()

    protected abstract fun onEnd()

}