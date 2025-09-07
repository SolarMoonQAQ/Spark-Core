package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.animation.IAnimatable

class BonePoseGroup(val model: ModelInstance): HashMap<String, BonePose>() {

    init {
        for (bone in model.origin.bones.values) {
            val pose = BonePose(model, bone.name)
            this[bone.name] = pose
        }
    }

    fun copy(): BonePoseGroup {
        val copy = BonePoseGroup(model)
        for ((key, value) in this) {
            copy[key] = value.copy()
        }
        return copy
    }
    
}