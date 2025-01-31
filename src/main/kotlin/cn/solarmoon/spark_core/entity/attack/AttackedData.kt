package cn.solarmoon.spark_core.entity.attack

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.entity.LivingEntity

import java.util.Optional

/**
 * 受击信息，不包含伤害源和伤害值，如果想调用这两个以检测直接找Entity的hurt方法插入即可
 * @param damageBox 触发该次攻击的几何体，可以通过几何体大小位置等信息实现想要的效果
 * @param damagedBody 该次攻击的几何体所击中的骨骼
 * @param extraData 可以附加额外数据到此次攻击
 */
data class AttackedData(
//    val damageBox: DGeom,
//    val damagedBody: DBody?,
//    val buffer: DContactBuffer,
    val extraData: CompoundTag = CompoundTag()
) {



}