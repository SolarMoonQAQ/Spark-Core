package cn.solarmoon.spark_core.entity.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack

/**
 * ### 技能
 * 技能为统一的对整体对象的命令实现，因此不在具体的实体中实现，而是作为全局方法进行注册以后调用（注册可用[cn.solarmoon.spark_core.entry_builder.ObjectRegister.skill]）
 *
 * 如果需要对每个不同释放技能的对象进行单独的根据技能的属性调整（比如每个实体对技能的内置cd是独立的），可以使用[SkillController]进行技能管理。
 *
 * 总之，技能本身是全局性质的，而技能控制器则可以独立在每个释放者中而有一些独立的参数。
 *
 * #### 特性
 * - 技能的种类可用tag进行区分（tag创建可见[cn.solarmoon.spark_core.data.SkillTagProvider]），例如可以通过一个“attack”的tag来表明此技能是攻击性技能，此时可用一个自定的isAttacking方法来方便判断是否为攻击性技能
 */
interface Skill<T> {

    fun activate(ob: T)

    fun tick(ob: T)

    /**
     * 是否正在释放技能，此值需要手动指定状态，不会影响技能本身的释放，但会影响控制器等别的地方对技能是否正在进行的判断
     */
    fun isPlaying(ob: T): Boolean

    val registryKey get() = SparkRegistries.SKILL.getKey(this) ?: throw NullPointerException("Skill ${this.javaClass::getSimpleName} not yet registered.")

    val resourceKey get() = SparkRegistries.SKILL.getResourceKey(this).get()

    val builtInRegistryHolder get() = SparkRegistries.SKILL.getHolder(resourceKey).get()

    fun `is`(tag: TagKey<Skill<*>>) = builtInRegistryHolder.`is`(tag)

}