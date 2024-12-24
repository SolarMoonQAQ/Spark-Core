package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation
import kotlin.properties.Delegates

/**
 * @param prefix 可自动播放动画的前缀，当加载的动画名中有此前缀时会自动作为可自动播放的动画根据[isValid]尝试播放
 */
abstract class AutoAnim<A: IAnimatable<*>>(
    val animatable: A,
    open val prefix: String
) {

    var animTrigger by Delegates.observable(false) { _, old, new -> if (old != new && !new) animatable.animController.stopAllAnimation() }

    /**
     * 该动画可用的条件，在此之前会自动检测该动画是否存在
     */
    abstract fun isValid(): Boolean

    abstract fun getAnimSuffixName(): String

    abstract fun getAllAnimNames(): Set<String>

    fun getAnimName() = "$prefix/${getAnimSuffixName()}"

    open fun isSinglePlay(anim: MixedAnimation): Boolean = false

    open fun getAnimation(): MixedAnimation {
        val modelPath = animatable.animData.modelPath
        return MixedAnimation(modelPath, getAnimName())
    }

    open fun isPlaying(level: Int = 0, filter: (MixedAnimation) -> Boolean = { true }): Boolean {
        return getAllAnimNames().any { animatable.animController.isPlaying(it, level, filter) }
    }

    /**
     * 尝试当满足条件时播放状态动画
     * @param stopFilter 对当前所有动画进行过滤决定是否要暂停
     * @param animModifier 对当前状态动画属性进行修改
     * @return 是否满足播放动画的条件
     */
    fun tryPlay(stopFilter: (MixedAnimation) -> Boolean = {true}, animModifier: (MixedAnimation) -> Unit = {}): Boolean {
        return tryPlay(0, stopFilter, animModifier)
    }

    /**
     * 尝试当满足条件时播放状态动画
     * @param stopFilter 对当前所有动画进行过滤决定是否要暂停
     * @param animModifier 对当前状态动画属性进行修改
     * @return 是否满足播放动画的条件
     */
    fun tryPlay(level: Int, stopFilter: (MixedAnimation) -> Boolean = {true}, animModifier: (MixedAnimation) -> Unit = {}): Boolean {
        return tryPlay(getAnimation(), level, stopFilter, animModifier)
    }

    /**
     * 尝试当满足条件时播放状态动画
     * @param stopFilter 对当前所有动画进行过滤决定是否要暂停
     * @param animModifier 对当前状态动画属性进行修改
     * @return 是否满足播放动画的条件
     */
    fun tryPlay(anim: MixedAnimation, level: Int, stopFilter: (MixedAnimation) -> Boolean = {true}, animModifier: (MixedAnimation) -> Unit = {}): Boolean {
        if (animatable.animData.animationSet.hasAnimation(anim.name) && (isValid() || level != 0)) {
            if (!animatable.animController.isPlaying(anim.name, level) { !it.isCancelled } || isSinglePlay(anim)) {
                val anim = anim.apply { animModifier.invoke(this); this.level = level }
                animatable.animController.stopAndAddAnimation(anim) { stopFilter.invoke(it) }
            }
            return true
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AutoAnim<*>) return false

        return prefix == other.prefix
    }

    override fun hashCode(): Int {
        return prefix.hashCode()
    }

}