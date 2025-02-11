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
    val nodes: List<Vec2>,
    val conditionList: Either<Set<String>, Set<String>> = Either.right(setOf()),
): SkillComponent() {

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
        return PreInputReleaseComponent(nodes, conditionList)
    }

    override fun onActive() {}

    override fun onUpdate() {
        val preInput = (skill.holder as? Entity)?.getPreInput() ?: return
        val time = requireQuery<() -> Double>("time").invoke()
        release(preInput, time)
    }

    override fun onEnd() {}

    companion object {
        val CODEC: MapCodec<PreInputReleaseComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().fieldOf("nodes").forGetter { it.nodes },
                Codec.either(
                    Codec.STRING.listOf().xmap({it.toSet()}, {it.toList()}).fieldOf("whitelist").codec(),
                    Codec.STRING.listOf().xmap({it.toSet()}, {it.toList()}).fieldOf("blacklist").codec()
                ).optionalFieldOf("condition", Either.right(setOf())).forGetter { it.conditionList }
            ).apply(it, ::PreInputReleaseComponent)
        }
    }
}