package cn.solarmoon.spark_core.preinput

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

class PreInputId(
    val name: String,
    val priority: Int = 0
) {

    override fun equals(other: Any?): Boolean = other is PreInputId && name == other.name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String {
        return "name: $name, priority: $priority"
    }

    companion object {
        val CODEC: Codec<PreInputId> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority }
            ).apply(it, ::PreInputId)
        }
    }

}