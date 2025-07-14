package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * 模块状态枚举
 * 支持序列化的模块状态
 */
enum class OModuleState {
    /** 已发现但未验证 */
    DISCOVERED,

    /** 依赖验证通过，等待启用 */
    VALIDATED,

    /** 已启用 */
    ENABLED,

    /** 已禁用 */
    DISABLED,

    /** 出现错误 */
    ERROR;

    companion object {

        @JvmStatic
        val CODEC: Codec<OModuleState> = Codec.STRING.xmap(
            { state -> valueOf(state.uppercase()) },
            { it.name.lowercase() }
        )

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OModuleState> {
            override fun decode(buffer: FriendlyByteBuf): OModuleState {
                return buffer.readEnum(OModuleState::class.java)
            }
            override fun encode(buffer: FriendlyByteBuf, value: OModuleState) {
                buffer.writeEnum(value)
            }
        }
    }
}