package cn.solarmoon.spark_core.util

import net.minecraft.resources.ResourceLocation

class MaskAllocator private constructor(
    private val id: ResourceLocation
) {

    private var nextBit = 0

    fun nextMask(): Long {
        if (nextBit >= Long.SIZE_BITS) {
            throw IllegalStateException("$id 的可分配掩码超出了最大值: ${Long.SIZE_BITS}")
        }
        return 1L shl nextBit++
    }

    companion object {
        private val allocators = mutableMapOf<ResourceLocation, MaskAllocator>()

        fun byId(id: ResourceLocation): MaskAllocator = allocators.getOrPut(id) { MaskAllocator(id) }
    }

}
