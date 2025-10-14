package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.util.readNullable
import cn.solarmoon.spark_core.util.writeNullable
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs
import java.util.function.Function

data class AbilityEvent(
    val tag: GameplayTag,                 // 事件类型
    val payload: Any? = null      // 自定义数据
) {


}