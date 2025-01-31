package cn.solarmoon.spark_core.skill.controller

import cn.solarmoon.spark_core.skill.Skill
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import ru.nsk.kstatemachine.state.DefaultState
import kotlin.properties.Delegates

/**
 * ### 技能控制器
 * 可以为任意单独对象放入技能控制器以进行特殊的技能控制
 *
 * 默认情况下，对于[net.neoforged.neoforge.attachment.IAttachmentHolder]，可以使用[SkillControllerHelper]中的方法来获取或添加技能控制器。
 * 并且已经实现了tick的调用，无需手动调用
 *
 * 需要注意的是，技能控制器最好同一时间只存在一个可用的，否则最好将大部分可能重复可用的控制器合并到一个控制器中，这是为了保证每次调用时只有一个控制器能被调用，从而简化控制器的管理。
 *
 * 当无法避免重复时，可以配合[priority]参数进行权重管理，当多个控制器满足时会优先选择权重值更大的。
 * @param T 该控制器的持有者类型
 */
abstract class SkillController<T>(val name: String) {

    abstract val holder: T

    private var loadMoment by Delegates.observable(false) { _, old, new ->
        if (old != new) {
            if (new) onEntry() else onExit()
        }
    }

    /**
     * 所有控制器可用的技能
     *
     * *注意：必须将所有技能添加到此列表中，否则一些方法可能不会返回期望的结果*
     */
    val allSkills = mutableMapOf<String, Skill<*>>()

    val allActiveSkills get() = allSkills.values.filter { it.isActive() }

    val firstActiveSkill get() = allSkills.values.firstOrNull { it.isActive() }

    /**
     * 技能控制器的优先级，当多个控制器满足时会优先选择权重值更大的。
     */
    open val priority = 0

    fun <T: Skill<*>> addSkill(skill: T) = skill.apply {
        if (allSkills.contains(this.name)) throw Exception("名为 $name 的技能已存在于此控制器，请换一个名称")
        allSkills.put(this.name, this)
    }

    /**
     * 控制器是否可用（影响控制器的获取以及[tick]方法的调用）
     */
    abstract fun isAvailable(): Boolean

    /**
     * 只当[isAvailable]返回true时执行
     */
    abstract fun tick()

    /**
     * 是否正在播放任意技能
     */
    fun isPlaying(): Boolean = allSkills.values.any { it.isActive() }

    /**
     * 只要技能控制器存在，就会不断执行
     */
    open fun baseTick() {
        loadMoment = isAvailable()
        allSkills.values.forEach { it.update() }
    }

    /**
     * 执行在物理线程，如果需要修改碰撞体则通过此方法
     */
    open fun physTick() {}

    /**
     * 当此技能控制器有效的一瞬间调用此方法
     */
    open fun onEntry() {}

    /**
     * 当此技能控制器失效的一瞬间调用此方法
     */
    open fun onExit() {}

    /**
     * 当技能控制器有效并将要受到伤害时触发
     */
    open fun onHurt(event: LivingIncomingDamageEvent) {}

}