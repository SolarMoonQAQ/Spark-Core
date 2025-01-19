package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.AnimController
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.registry.common.SparkDataComponents
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class ItemAnimatable(override var modelIndex: ModelIndex): IAnimatable<ItemAnimatable> {

    var oPosition = Vec3.ZERO
    var position = Vec3.ZERO
    var oYRot = 0f
    var yRot = 0f

    fun updatePos(pos: Vec3) {
        oPosition = position
        position = pos
    }

    override val animatable = this

    override val animController: AnimController = AnimController(this)

    override val bones: BoneGroup = BoneGroup(this)

    override fun getWorldPosition(partialTick: Float): Vec3 {
        return oPosition.lerp(position, partialTick.toDouble())
    }

    override fun getRootYRot(partialTick: Float): Float {
        return 0f
    }

    override fun equals(other: Any?): Boolean {
        return (other as? ItemAnimatable)?.modelIndex == modelIndex
    }

    override fun hashCode(): Int {
        return modelIndex.hashCode()
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<ItemAnimatable> = RecordCodecBuilder.create {
            it.group(
                ModelIndex.CODEC.fieldOf("model_index").forGetter { it.modelIndex },
                Vec3.CODEC.fieldOf("position").forGetter { it.position }
            ).apply(it) { index, pos -> ItemAnimatable(index).apply { updatePos(pos) } }
        }

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ModelIndex.STREAM_CODEC, ItemAnimatable::modelIndex,
            SerializeHelper.VEC3_STREAM_CODEC, ItemAnimatable::position,
            { index, op -> ItemAnimatable(index).apply { updatePos(op) } }
        )
    }

}