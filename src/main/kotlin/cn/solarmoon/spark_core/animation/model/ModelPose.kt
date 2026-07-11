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

    /**
     * 骨骼姿态预缓存列表，与 [bonePoses] 存储相同的 BonePose 实例（同一引用），
     * 顺序与 origin.bones 一致。热路径直接 for 循环遍历，消除双重映射开销。
     */
    val bonePoseList: List<BonePose>

    init {
        val entries = ArrayList<BonePose>(model.origin.bones.size)
        for (bone in model.origin.bones.values) {
            val pose = BonePose(model, bone.name)
            bonePoses[bone.name] = pose
            entries.add(pose)
        }
        this.bonePoseList = entries
    }

    fun getBonePose(name: String) = bonePoses[name]!! // 理论上骨骼组创建时已经根据当前origin获取了所有骨骼，所以不存在不存在的骨骼

    fun getBonePoseOrCreateEmpty(name: String) = bonePoses.getOrPut(name) { BonePose(model, name) }

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
        return model.animatable.getWorldPositionMatrix(partialTicks.toFloat()).mul(getSpaceBoneLocatorMatrix(name, partialTicks))
    }

    fun getWorldLocator(name: String, offset: Vec3 = Vec3.ZERO, partialTicks: Number = 1.0): Vector3f {
        return model.animatable.getWorldPositionMatrix(partialTicks.toFloat()).transformPosition(getSpaceLocator(name, offset, partialTicks))
    }

    fun copy(): ModelPose {
        val copy = ModelPose(model)
        for ((key, value) in bonePoses) {
            copy.bonePoses[key] = value.copy()
        }
        return copy
    }
    
}