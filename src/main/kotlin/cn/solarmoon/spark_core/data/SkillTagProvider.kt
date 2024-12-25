package cn.solarmoon.spark_core.data

import cn.solarmoon.spark_core.entity.skill.Skill
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.tags.IntrinsicHolderTagsProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.neoforged.neoforge.common.data.ExistingFileHelper
import java.util.concurrent.CompletableFuture

abstract class SkillTagProvider(
    output: PackOutput,
    lookupProvider: CompletableFuture<HolderLookup.Provider>,
    modId: String,
    existingFileHelper: ExistingFileHelper
): IntrinsicHolderTagsProvider<Skill<*>>(output, SparkRegistries.SKILL.key(), lookupProvider, { it.resourceKey }, modId, existingFileHelper) {

    companion object {
        @JvmStatic
        fun createTag(location: ResourceLocation) = TagKey.create(SparkRegistries.SKILL.key(), location)
    }

}