package cn.solarmoon.spark_core.entity.skill

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
 */
abstract class SkillController {

    private var loadMoment by Delegates.observable(false) { _, old, new ->
        if (old != new) {
            if (new) onLoadedMoment() else onDisabledMoment()
        }
    }

    /**
     * 技能控制器的优先级，当多个控制器满足时会优先选择权重值更大的。
     */
    open val priority = 0

    /**
     * 控制器是否可用（影响控制器的获取以及[tick]方法的调用）
     */
    abstract fun isAvailable(): Boolean

    /**
     * 只当[isAvailable]返回true时执行
     */
    abstract fun tick()

    /**
     * 只要技能控制器存在，就会不断执行
     */
    open fun baseTick() {
        loadMoment = isAvailable()
    }

    /**
     * 当此技能控制器有效的一瞬间调用此方法
     */
    open fun onLoadedMoment() {}

    /**
     * 当此技能控制器失效的一瞬间调用此方法
     */
    open fun onDisabledMoment() {}

}