package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable

class BonePoseGroup(val animatable: IAnimatable<*>): HashMap<String, BonePose>() {

    init {
        animatable.model.bones.forEach {
            put(it.key, it.value.createDefaultBone(animatable))
        }
    }
    
    fun copy(): BonePoseGroup {
        val copy = BonePoseGroup(animatable)
        for ((key, value) in this) {
            copy[key] = value.copy()
        }
        return copy
    }
    
}