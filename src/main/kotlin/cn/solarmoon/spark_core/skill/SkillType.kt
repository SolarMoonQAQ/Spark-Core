package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.payload.SkillPredictPayload
import cn.solarmoon.spark_core.skill.payload.SkillSyncPayload
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.collections.set

/**
 * 脚本来源信息
 * 用于追踪技能是由哪个脚本文件创建的
 */
data class ScriptSource(
    val apiId: String,       // API模块ID，如"skill"
    val fileName: String     // 脚本文件名，如"axe_combo_1"
)

class SkillType<S: Skill>(
    val registryKey: ResourceLocation,
    val isIndependent: Boolean = true,
    val conditions: List<SkillStartCondition> = listOf(),
    internal val provider: () -> S,
) {

    var fromScript = false
        internal set

    /**
     * 脚本来源信息
     * 如果技能是由JS脚本创建的，此字段记录来源脚本信息
     * 非JS创建的技能此字段为null
     */
    var scriptSource: ScriptSource? = null
    val name = Component.translatable("skill.${registryKey.namespace}.${registryKey.path}.name")
    val description = Component.translatable("skill.${registryKey.namespace}.${registryKey.path}.description")
    val icon = ResourceLocation.fromNamespaceAndPath(registryKey.namespace, "textures/skill/${registryKey.path}.png")

    fun createSkill(holder: SkillHost, level: Level, active: Boolean = false): Skill? {
        // 在客户端尝试启用时，视为技能预测，本地条件不满足自然不会预测，也不会发送同步包。但客户端条件满足，会发送预测同步包，服务端收到技能预测包时，会尝试启用技能，启用成功则当前客户端与服务端都启用了，并且服务端会发包将当前预测技能放入正式技能表中，否则则返回技能拒绝包，拒绝包会立刻终止并删除当前预测技能
        // 在服务端尝试启用时，尝试检查技能是否可用，可用则直接启用技能并同步到客户端
        conditions.forEach {
            try {
                it.test(holder, level)
            } catch (e: SkillStartRejectedException) {
                SparkCore.LOGGER.error("$holder 的技能 $registryKey 在 ${Thread.currentThread().name} ${if (active) "启用" else "创建"}失败：${e.reason}")
                provider().init(0, this, holder, level).triggerEvent(SkillEvent.Rejected(it))
                return null
            }
        }

        var result: Skill
        // 客户端在本地启用以进行技能预测，发送给服务端进行同步，如果服务端拒绝，则会发送拒绝指令使预测失效
        if (level.isClientSide) {
            val id = holder.skillCount.decrementAndGet()
            result = provider().init(id, this, holder, level)
            holder.predictedSkills[id] = result
            PacketDistributor.sendToServer(SkillPredictPayload(holder, this, id, active))
        }
        // 服务端启用技能，然后同步给客户端，服务端无所谓预测，只要满足条件直接同步
        else {
            val id = holder.skillCount.incrementAndGet()
            result = provider().init(id, this, holder, level)
            holder.allSkills[id] = result
            PacketDistributor.sendToAllPlayers(SkillSyncPayload(holder, this, id, active))
        }
        if (active) result.activate()
        return result
    }

    fun createSkillWithoutSync(id: Int, holder: SkillHost, level: Level): Skill {
        return provider().init(id, this, holder, level).apply {
            holder.allSkills[id] = this
        }
    }

    companion object {
        val STREAM_CODEC = ByteBufCodecs.INT.map(
            { SkillManager.values.elementAt(it) },
            { SkillManager.values.indexOf(it) }
        )
    }

}