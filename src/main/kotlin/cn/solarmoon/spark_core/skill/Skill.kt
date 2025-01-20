package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.skill.controller.SkillController
import cn.solarmoon.spirit_of_fight.feature.fight_skill.skill.SkillComponent

/**
 * ### 技能
 * 技能是为某个特定对象进行命令操作的类，该类本身对特定技能持有者而言是需要实例化的，但通过[SkillType]进行注册后能够实现抽象化（类似[net.minecraft.world.entity.EntityType]与[net.minecraft.world.entity.Entity]的关系）
 *
 * 技能本身不作任何的方法调用，单独使用都需要在指定持有者中创建新实例（[SkillType.create])并自行调用。**但可以通过[cn.solarmoon.spark_core.skill.controller.SkillController]来进行统一管控，其中默认实现了skill的[update]调用以及其它的实用内容**
 * #### 特性
 * - 技能的种类可用tag进行区分（tag创建可见[cn.solarmoon.spark_core.data.SkillTagProvider]），例如可以通过一个“attack”的tag来表明此技能是攻击性技能，此时可用一个自定的isAttacking方法来方便判断是否为攻击性技能
 * @see cn.solarmoon.spark_core.skill.controller.SkillController
 */
interface Skill<T> {

    val holder: T

    val components: MutableList<SkillComponent>

    val runTime: Int

    fun isActive(): Boolean

    fun activate()

    fun update()

    fun end()

    fun <T: SkillComponent> addComponent(component: T) = component.apply { components.add(this) }

}