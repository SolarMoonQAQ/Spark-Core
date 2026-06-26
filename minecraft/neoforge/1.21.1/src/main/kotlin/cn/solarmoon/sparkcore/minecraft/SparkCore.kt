package cn.solarmoon.sparkcore.minecraft

import cn.solarmoon.sparkcore.minecraft.entry_builder.ObjectRegister
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.slf4j.LoggerFactory

@Mod(SparkCore.MOD_ID)
class SparkCore(
    val bus: IEventBus,
    val modContainer: ModContainer
) {

    companion object {
        const val MOD_ID = "sparkcore"
        val LOGGER = LoggerFactory.getLogger("星火核心")
        val REGISTRY = ObjectRegister(MOD_ID)

        fun logger(prefix: String) = LoggerFactory.getLogger("星火核心/$prefix")
    }

    init {
        Matrix4f().scale(1f, 1f, 1f).translateLocal(0f, 0f, 0f)
        Quaternionf().rotateLocalX(0f)
    }

}