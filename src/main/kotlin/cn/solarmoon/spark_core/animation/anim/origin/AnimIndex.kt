package cn.solarmoon.spark_core.animation.anim.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation

data class AnimIndex(
    val inputPath: ResourceLocation,
    val name: String,
    val useShortcutConversion: Boolean = true
) {
    /**
     * 实际的动画集路径，经过快捷路径转换处理
     */
    val index: ResourceLocation = if (useShortcutConversion) {
        // 尝试从ORIGINS中查找完整路径
        val shortcutPath = ResourceLocation.fromNamespaceAndPath(inputPath.namespace, "${inputPath.path}/$name")
        ORIGINS[shortcutPath] ?: inputPath
    } else {
        inputPath
    }

    companion object {
        val CODEC: Codec<AnimIndex> = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("inputPath").forGetter { it.inputPath },
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.BOOL.optionalFieldOf("useShortcutConversion", false).forGetter { it.useShortcutConversion }
            ).apply(it, ::AnimIndex)
        }

        /**
         * 快捷路径映射表：minecraft:entityPath/animName -> 完整动画集路径
         * 例如：minecraft:player/walk -> spark_core:sparkcore/animations/player
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, ResourceLocation>()
    }

    val locationName get() = "$index/${name.replace(regex = Regex("[^a-z0-9/._-]"), "_")}"

    override fun toString(): String {
        return "$index/$name"
    }

}