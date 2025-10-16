package cn.solarmoon.spark_core.gas

import net.minecraft.network.codec.ByteBufCodecs

/**
 * 标签集合
 */
data class GameplayTagContainer(
    private val tags: MutableSet<GameplayTag> = mutableSetOf<GameplayTag>()
) {

    fun add(tag: GameplayTag) = tags.add(tag)

    fun remove(tag: GameplayTag) = tags.remove(tag)

    fun has(tag: GameplayTag) = tags.any { it.matches(tag) }

    fun all(): Set<GameplayTag> = tags.toSet()

    fun isEmpty() = tags.isEmpty()

    override fun toString() = tags.joinToString(", ")

    operator fun contains(tag: GameplayTag): Boolean = has(tag)

    companion object {
        val CODEC = GameplayTag.CODEC.listOf().xmap({ GameplayTagContainer(it.toMutableSet()) }, { it.tags.toList() })

        val STREAM_CODEC = GameplayTag.STEAM_CODEC.apply(ByteBufCodecs.list()).map({ GameplayTagContainer(it.toMutableSet()) }, { it.tags.toList() })
    }

}
