package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance

abstract class AnimSkill<T: IAnimatable<*>>(
    holder: T
): BaseSkill<T>(holder) {

    val allAnims: MutableList<AnimInstance> = mutableListOf()

    override fun onActivate() {
        allAnims.forEach { it.refresh() }
        super.onActivate()
    }

    fun createAnimInstance(name: String, provider: AnimInstance.() -> Unit = {}): AnimInstance {
        val anim = holder.newAnimInstance(name, provider)
        allAnims.add(anim)
        return anim
    }

}