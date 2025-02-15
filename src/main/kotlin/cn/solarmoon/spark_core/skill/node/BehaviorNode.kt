package cn.solarmoon.spark_core.skill.node

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Function

abstract class BehaviorNode {

    private var entryCheck = false
    var parent: BehaviorNode? = null
    lateinit var skill: SkillInstance
    var ordinal = 0
    val dynamicContainer = DynamicNodeContainer(this)

    fun tick(skill: SkillInstance): NodeStatus {
        if (!entryCheck) {
            this.skill = skill
            onStart(skill)
            entryCheck = true
        }
        return onTick(skill)
    }

    fun end(skill: SkillInstance) {
        entryCheck = false
        onEnd(skill)
        dynamicContainer.children.forEach {
            it.end(skill)
        }
    }

    fun refresh() {
        entryCheck = false
        onRefresh()
        dynamicContainer.children.forEach {
            it.refresh()
        }
    }

    fun mountOnTree(tree: BehaviorTree) {
        tree.mountNode(this)

        dynamicContainer.children.forEach {
            it.mountOnTree(tree)
        }
    }

    protected open fun onStart(skill: SkillInstance) {}

    protected abstract fun onTick(skill: SkillInstance): NodeStatus

    protected open fun onEnd(skill: SkillInstance) {}

    protected open fun onRefresh() {}

    open fun sync(host: SkillHost, data: CompoundTag, context: IPayloadContext) {}

    protected fun <T> read(key: String) = skill.behaviorTree.blackBoard.get<T>(key)

    protected fun write(key: String, value: Any) = skill.behaviorTree.blackBoard.set(key, value)

    protected inline fun <reified T> require(key: String) = read<T>(key) ?: throw NullPointerException("行为节点 ${javaClass.simpleName} 必须拥有一个 $key(${T::class.simpleName}) 黑板数据")

    abstract val codec: MapCodec<out BehaviorNode>

    abstract fun copy(): BehaviorNode

    companion object {
        val CODEC = SparkRegistries.BEHAVIOR_NODE_CODEC.byNameCodec()
            .dispatch(
                BehaviorNode::codec,
                Function.identity()
            )
    }

}