package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import net.minecraft.world.phys.Vec3

/**
 * 继承了该接口的物品将在entity的物品栏的tick中不断更新自身在世界的位置坐标
 */
interface IAnimatableItem {

    fun getPosition(event: ItemStackInventoryTickEvent): Vec3

    fun onUpdate(oldAnimatable: ItemAnimatable, event: ItemStackInventoryTickEvent)

}