package cn.solarmoon.spark_core.animation.model

import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.collections.HashMap
import kotlin.text.get

class ModelPose(
    val model: ModelInstance
) {

    val bonePoses = hashMapOf<String, BonePose>()

    init {
        for (bone in model.origin.bones.values) {
            val pose = BonePose(model, bone.name)
            bonePoses[bone.name] = pose
        }
    }

    fun getBonePose(name: String) = bonePoses[name]!! // 理论上骨骼组创建时已经根据当前origin获取了所有骨骼，所以不存在不存在的骨骼

    fun getBonePoseOrCreateEmpty(name: String) = bonePoses.getOrPut(name) { BonePose(model, name) }

    fun getWorldPositionMatrix(partialTicks: Number = 1f): Matrix4f {
        val animatable = model.animatable
        return Matrix4f().translate(animatable.getWorldPosition(partialTicks).toVector3f()).rotateY(animatable.getRootYRot(partialTicks))
    }

    fun getSpaceBoneLocatorMatrix(name: String, partialTicks: Number = 1.0): Matrix4f {
        val locator = model.origin.locators[name] ?: return Matrix4f()
        val spaceMatrix = getBonePose(locator.bone.name).getSpaceBoneMatrix(partialTicks)
        return spaceMatrix
            .translate(locator.offset.toVector3f())
            .rotateZYX(locator.rotation.toVector3f())
    }

    fun getSpaceLocator(name: String, offset: Vec3 = Vec3.ZERO, partialTicks: Number = 1.0): Vector3f {
        val locatorMatrix = getSpaceBoneLocatorMatrix(name, partialTicks)
        return locatorMatrix.transformPosition(offset.toVector3f())
    }

    fun getWorldBoneLocatorMatrix(name: String, partialTicks: Number = 1.0): Matrix4f {
        return getWorldPositionMatrix(partialTicks.toFloat()).mul(getSpaceBoneLocatorMatrix(name, partialTicks))
    }

    fun getWorldLocator(name: String, offset: Vec3 = Vec3.ZERO, partialTicks: Number = 1.0): Vector3f {
        return getWorldPositionMatrix(partialTicks.toFloat()).transformPosition(getSpaceLocator(name, offset, partialTicks))
    }

    fun copy(): ModelPose {
        val copy = ModelPose(model)
        for ((key, value) in bonePoses) {
            copy.bonePoses[key] = value.copy()
        }
        return copy
    }
    
}