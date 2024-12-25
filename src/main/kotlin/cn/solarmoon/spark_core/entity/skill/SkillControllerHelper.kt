package cn.solarmoon.spark_core.entity.skill

import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.neoforged.neoforge.attachment.IAttachmentHolder

object SkillControllerHelper {}

fun IAttachmentHolder.getAllSkillControllers() = getData(SparkAttachments.SKILL_CONTROLLER)

/**
 * 获取第一个符合条件的技能控制器
 */
fun IAttachmentHolder.getSkillController() = getAllSkillControllers()
    .filter { it.isAvailable() }
    .sortedByDescending { it.priority } // 按照优先级降序排序
    .firstOrNull() // 选择优先级最高的一个

fun IAttachmentHolder.addSkillController(controller: SkillController) = getAllSkillControllers().add(controller)