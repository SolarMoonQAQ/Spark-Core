package cn.solarmoon.spark_core.skill.module

import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.entity.preinput.PreInput
import cn.solarmoon.spark_core.entity.preinput.getPreInput
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import kotlin.collections.forEach
import kotlin.collections.toList
import kotlin.collections.toSet
import kotlin.ranges.contains

data class PreInputReleaseModule(
    val nodes: List<Vec2> = listOf(),
    val conditionList: Either<Set<String>, Set<String>> = Either.right(setOf()),
) {

    fun tryRelease(preInput: PreInput, time: Double) {
        val whitelist = conditionList.left()
        val blacklist = conditionList.right()

        fun release() {
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

        if (nodes.isEmpty()) release()
        else nodes.forEach {
            if (time in it.x..it.y) {
                release()
            }
        }
    }

    companion object {
        val CODEC: Codec<PreInputReleaseModule> = RecordCodecBuilder.create {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("nodes", listOf()).forGetter { it.nodes },
                Codec.either(
                    Codec.STRING.listOf().xmap({it.toSet()}, {it.toList()}).fieldOf("whitelist").codec(),
                    Codec.STRING.listOf().xmap({it.toSet()}, {it.toList()}).fieldOf("blacklist").codec()
                ).optionalFieldOf("condition", Either.right(setOf())).forGetter { it.conditionList }
            ).apply(it, ::PreInputReleaseModule)
        }
    }
}