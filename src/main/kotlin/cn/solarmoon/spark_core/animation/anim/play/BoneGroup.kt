package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable

class BoneGroup(val animatable: IAnimatable<*>): HashMap<String, Bone>() {

    init {
        animatable.model.bones.forEach {
            put(it.key, it.value.createDefaultBone(animatable))
        }
    }
    
    fun copy(): BoneGroup {
        val copy = BoneGroup(animatable)
        for ((key, value) in this) {
            copy[key] = value.copy()
        }
        return copy
    }
    
}