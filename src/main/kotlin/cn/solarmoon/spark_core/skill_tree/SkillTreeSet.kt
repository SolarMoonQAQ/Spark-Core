package cn.solarmoon.spark_core.skill_tree

import net.minecraft.client.player.Input
import net.minecraft.world.entity.player.Player

class SkillTreeSet: LinkedHashSet<SkillTree> {
    constructor() : super()
    constructor(elements: Collection<SkillTree>) : super(elements)

//    fun tryAdvance(player: Player, input: Input, simulate: Boolean = false): Boolean {
////        // 修复：旁观者模式下卡操作问题
////        if (player.isSpectator) return false
////
////        // 使用 partition 分割列表
////        val (activeTrees, inactiveTrees) = this.partition { it.currentNode != null }
////
////        // 优先处理 activeTrees
////        if (activeTrees.any { it.tryAdvance(player, input, simulate) }) {
////            return true
////        }
////
////        // 若未成功，再处理 inactiveTrees
////        return inactiveTrees.any { it.tryAdvance(player, input, simulate) }
//    }

    fun get() = SkillTreeSet(sortedByDescending { it.priority })

}