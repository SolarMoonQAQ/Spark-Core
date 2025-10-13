package cn.solarmoon.spark_core.gas

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

}
