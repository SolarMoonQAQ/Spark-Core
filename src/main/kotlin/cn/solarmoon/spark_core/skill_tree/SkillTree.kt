package cn.solarmoon.spark_core.skill_tree

import cn.solarmoon.spark_core.preinput.PreInput
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill_tree.node.SkillTreeNode
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

/**
 * ### 技能树
 * 技能树为技能之间连携释放的桥梁，会自动推进下一个技能的释放，根据不同的条件从而推出不同的技能释放路径分支
 *
 * 为了保证技能树的稳定性，它被设计于在单一线程工作。一般而言，玩家操作为了在客户端反馈及时，被设计为仅在客户端推进并同步映射到服务端，而其它技能持有者则从服务端推进，映射到客户端
 *
 * 此外，可以通过js脚本更方便的编写与重载技能树
 */
class SkillTree(
    val registryKey: ResourceLocation,
    val rootNodes: List<SkillTreeNode>,
    val priority: Int = 0
) {

    lateinit var root: SkillTreeSet

    private val path = ArrayDeque<Int>()

    var currentSkill: Skill? = null
    var currentNode: SkillTreeNode? = null
        private set
    var reserveTime = 0
        private set

    val name = Component.translatable("skill.tree.${registryKey.namespace}.${registryKey.path}.name")
    val icon = ResourceLocation.fromNamespaceAndPath(registryKey.namespace, "textures/skill/tree/${registryKey.path}.png")

    fun tryAdvance(host: SkillHost, level: Level, simulate: Boolean = false): Boolean {
        val cNode = currentNode
        val preInput = host.preInput

        val ps = currentSkill
        if (ps != null && !ps.isActivated) {
            if (reserveTime > 0) reserveTime--
            else reset()
        }

        // 阶段1：首次触发根节点
        if (cNode == null) {
            rootNodes.forEachIndexed { index, rootNode ->
                if (rootNode.match(host, currentSkill)) {
                    if (!simulate) activateNode(rootNode, index, host, level, preInput, true)
                    return true
                }
            }
        }
        // 阶段2：触发子节点
        else {
            cNode.children.forEachIndexed { index, child ->
                if (child.match(host, currentSkill)) {
                    if (!simulate) activateNode(child, index, host, level, preInput, false)
                    return true
                }
            }
        }

        return false
    }

    private fun activateNode(
        node: SkillTreeNode,
        index: Int,
        host: SkillHost,
        level: Level,
        preInput: PreInput,
        root: Boolean
    ) {
        preInput.setInput(node.preInputId, node.preInputDuration) {
            val next = if (!root) currentNode?.nextNode(index) else rootNodes.getOrNull(index)
            if (next?.onEntry(host, level, this) == true) {
                reserveTime = node.reserveTime

                path.add(index)
                currentNode = next
            }
        }
    }

    fun reset() {
        path.clear()
        reserveTime = 0
        currentSkill = null
        currentNode = null
    }

    fun getNodeByPath(path: MutableList<Int>): SkillTreeNode? {
        if (path.isEmpty()) return null

        var result = rootNodes.getOrNull(path.removeFirst())
        while (path.isNotEmpty()) {
            result = result?.children?.getOrNull(path.removeFirst())
        }

        return result
    }

}