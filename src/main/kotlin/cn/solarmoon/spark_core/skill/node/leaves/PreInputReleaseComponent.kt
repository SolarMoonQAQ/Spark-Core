package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.entity.preinput.PreInput
import cn.solarmoon.spark_core.entity.preinput.getPreInput
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
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
    val nodes: List<Vec2> = listOf(),
    val conditionList: Either<Set<String>, Set<String>> = Either.right(setOf()),
): BehaviorNode() {

    override val codec: MapCodec<out BehaviorNode> = CODEC

    fun release(preInput: PreInput, time: Double) {
        if (nodes.isEmpty()) {
            doRelease(preInput)
        } else {
            nodes.forEach {
                if (time in it.x..it.y) {
                    doRelease(preInput)
                }
            }
        }
    }

    fun doRelease(preInput: PreInput) {
        val whitelist = conditionList.left()
        val blacklist = conditionList.right()
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

    override fun copy(): BehaviorNode {
        return PreInputReleaseComponent(nodes, conditionList)
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        val preInput = (skill.holder as? Entity)?.getPreInput() ?: return NodeStatus.FAILURE
        val time = require<() -> Double>("time").invoke()
        release(preInput, time)
        return NodeStatus.RUNNING
    }

    companion object {
        val CODEC: MapCodec<PreInputReleaseComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("nodes", listOf()).forGetter { it.nodes },
                Codec.either(
                    Codec.STRING.listOf().xmap({it.toSet()}, {it.toList()}).fieldOf("whitelist").codec(),
                    Codec.STRING.listOf().xmap({it.toSet()}, {it.toList()}).fieldOf("blacklist").codec()
                ).optionalFieldOf("condition", Either.right(setOf())).forGetter { it.conditionList }
            ).apply(it, ::PreInputReleaseComponent)
        }
    }
}