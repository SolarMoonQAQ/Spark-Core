package cn.solarmoon.spark_core.event

import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.Event

class ItemStackInventoryTickEvent(val stack: ItemStack, val entity: Entity, val inventorySlot: Int, val isCurrentItem: Boolean): Event() {
}