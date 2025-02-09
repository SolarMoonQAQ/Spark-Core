package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.entity.preinput.PreInput
import cn.solarmoon.spark_core.entity.preinput.getPreInput
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import kotlin.collections.forEach
import kotlin.ranges.contains
import kotlin.ranges.rangeTo

class PreInputReleaseComponent(
    val timeType: String,
    val nodes: List<Vec2>,
    val conditionList: Either<List<String>, List<String>> = Either.right(listOf()),
    children: List<SkillComponent> = listOf(),
): SkillComponent(children) {

    override val codec: MapCodec<out SkillComponent> = CODEC

    fun release(preInput: PreInput, time: Double) {
        val whitelist = conditionList.left()
        val blacklist = conditionList.right()

        nodes.forEach {
            if (time in it.x..it.y) {
                if (whitelist.isPresent) {
                    whitelist.get().forEach {
                        preInput.executeIfPresent(it)
                    }
                    return
                }
                else if (blacklist.isPresent) {
                    if (preInput.id !in blacklist.get()) preInput.executeIfPresent()
                }
            }
        }
    }

    override fun copy(): SkillComponent {
        return PreInputReleaseComponent(timeType, nodes, conditionList, children)
    }

    override fun onActive(): Boolean {
        return true
    }

    override fun onUpdate(): Boolean {
        val preInput = (skill.holder as? Entity)?.getPreInput() ?: return false
        val time = if (timeType == "anim") registerContext(AnimInstance::class).time else skill.runTime.toDouble()
        release(preInput, time)
        return true
    }

    override fun onEnd(): Boolean {
        return true
    }

    companion object {
        val CODEC: MapCodec<PreInputReleaseComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.optionalFieldOf("time_type", "skill").forGetter { it.timeType },
                SerializeHelper.VEC2_CODEC.listOf().fieldOf("nodes").forGetter { it.nodes },
                Codec.either(Codec.STRING.listOf().fieldOf("whitelist").codec(), Codec.STRING.listOf().fieldOf("blacklist").codec()).optionalFieldOf("condition", Either.right(
                    listOf()
                )).forGetter { it.conditionList },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::PreInputReleaseComponent)
        }
    }
}