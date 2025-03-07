package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.preinput.PreInputId
import cn.solarmoon.spark_core.skill.SkillTimeLine
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import kotlin.collections.forEach
import kotlin.collections.toList
import kotlin.collections.toSet

class PreInputReleaseComponent(
    val nodes: List<SkillTimeLine.Stamp> = listOf(),
    val conditionList: Either<Set<PreInputId>, Set<PreInputId>> = Either.right(setOf()),
): SkillComponent() {

    override fun onTick() {
        val whitelist = conditionList.left()
        val blacklist = conditionList.right()
        val entity = skill.holder as? Entity ?: return
        val preInput = entity.preInput

        if (skill.timeline.match(nodes)) {
            if (whitelist.isPresent) {
                whitelist.get().forEach {
                    preInput.executeIfPresent(it)
                }
                return
            }
            else if (blacklist.isPresent) {
                if (blacklist.get().all { !preInput.hasInput(it) }) preInput.execute()
            }
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PreInputReleaseComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SkillTimeLine.Stamp.CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.nodes },
                Codec.either(
                    Codec.STRING.listOf().xmap({it.map { PreInputId(it) }.toSet()}, {it.map { it.name }.toList()}).fieldOf("whitelist").codec(),
                    Codec.STRING.listOf().xmap({it.map { PreInputId(it) }.toSet()}, {it.map { it.name }.toList()}).fieldOf("blacklist").codec()
                ).optionalFieldOf("condition", Either.right(setOf())).forGetter { it.conditionList }
            ).apply(it, ::PreInputReleaseComponent)
        }
    }
}