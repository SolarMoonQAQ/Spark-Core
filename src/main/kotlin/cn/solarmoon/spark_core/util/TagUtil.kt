package cn.solarmoon.spark_core.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

fun CompoundTag.getVec3(key: String): Vec3 {
    val list = this.getList(key, 6) // 6 = NBT double 类型
    return Vec3(
        list.getDouble(0),
        list.getDouble(1),
        list.getDouble(2)
    )
}

fun CompoundTag.putVec3(key: String, vec: Vec3) {
    val list = ListTag()
    list.add(DoubleTag.valueOf(vec.x))
    list.add(DoubleTag.valueOf(vec.y))
    list.add(DoubleTag.valueOf(vec.z))
    this.put(key, list)
}

fun CompoundTag.getQuaternionf(key: String): Quaternionf {
    val list = this.getList(key, 5)
    return Quaternionf(
        list.getFloat(0),
        list.getFloat(1),
        list.getFloat(2),
        list.getFloat(3)
    )
}

fun CompoundTag.putQuaternionf(key: String, quat: Quaternionf) {
    val list = ListTag()
    list.add(FloatTag.valueOf(quat.x))
    list.add(FloatTag.valueOf(quat.y))
    list.add(FloatTag.valueOf(quat.z))
    list.add(FloatTag.valueOf(quat.w))
    this.put(key, list)
}

fun CompoundTag.ifContains(key: String, block: CompoundTag.(String) -> Unit) {
    if (this.contains(key)) {
        block(key)
    }
}
