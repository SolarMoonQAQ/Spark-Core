package cn.solarmoon.spark_core.util

import com.mojang.datafixers.util.Either
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey

typealias ResOrTag<T> = Either<ResourceLocation, TagKey<T>>

fun <T> String.parseResOrTag(registryKey: ResourceKey<out Registry<T>>): ResOrTag<T> {
    return if (startsWith("#")) {
        val id = ResourceLocation.parse(substring(1))
        val tagKey = TagKey.create(registryKey, id)
        Either.right(tagKey)
    } else {
        val id = ResourceLocation.parse(this)
        Either.left(id)
    }
}