package cn.solarmoon.spark_core.phys

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.tags.TagKey

class BodyType {

    val registryKey get() = SparkRegistries.BODY_TYPE.getKey(this)

    val resourceKey get() = SparkRegistries.BODY_TYPE.getResourceKey(this).get()

    val builtInRegistryHolder get() = SparkRegistries.BODY_TYPE.getHolder(resourceKey).get()

    fun `is`(tag: TagKey<BodyType>) = builtInRegistryHolder.`is`(tag)

    override fun equals(other: Any?): Boolean {
        return (other as? BodyType)?.registryKey == registryKey
    }

    override fun hashCode(): Int {
        return registryKey.hashCode()
    }

}