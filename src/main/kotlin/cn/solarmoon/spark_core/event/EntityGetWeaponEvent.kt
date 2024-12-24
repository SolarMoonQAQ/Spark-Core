package cn.solarmoon.spark_core.event

import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.event.entity.EntityEvent

/**
 * 注入在[Entity.getWeaponItem]
 */
class EntityGetWeaponEvent(entity: Entity, var weapon: ItemStack?): EntityEvent(entity) {
}