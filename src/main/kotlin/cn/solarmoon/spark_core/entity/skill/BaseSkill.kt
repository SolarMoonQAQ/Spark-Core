package cn.solarmoon.spark_core.entity.skill

/**
 * 对技能实现了基本的equal比较器
 * @see Skill
 */
abstract class BaseSkill<T>: Skill<T> {

    override fun equals(other: Any?): Boolean {
        return (other as? Skill<*>)?.resourceKey == this.resourceKey
    }

    override fun hashCode(): Int {
        return resourceKey.hashCode()
    }

}