package cn.solarmoon.spark_core.skill.condition

import cn.solarmoon.spark_core.skill.SkillHost
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.crafting.Ingredient

class HoldItemCondition(
    val ingredient: Ingredient,
    val hand: InteractionHand
): SkillCondition {

    override val codec: MapCodec<out SkillCondition> = CODEC

    override fun test(holder: SkillHost): Boolean {
        return holder is LivingEntity && ingredient.test(holder.getItemInHand(hand))
    }

    companion object {
        val CODEC: MapCodec<HoldItemCondition> = RecordCodecBuilder.mapCodec {
            it.group(
                Ingredient.CODEC.fieldOf("hold").forGetter { it.ingredient },
                Codec.STRING.xmap(
                    { InteractionHand.valueOf(it.uppercase()) },
                    { it.toString().lowercase() }
                ).fieldOf("hand").forGetter { it.hand }
            ).apply(it, ::HoldItemCondition)
        }
    }

}