package cn.solarmoon.spark_core.lua

import li.cil.repack.com.naef.jnlua.LuaState
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Value
import java.nio.charset.StandardCharsets

class LuaScript(
    val index: ResourceLocation,
    val content: ByteArray
) {

    val stringContent = content.toString(StandardCharsets.UTF_8)

    internal var value: LuaState? = null

    fun getValueOrThrow() = value ?: throw NullPointerException("脚本 $index 尚未被解析，解析值不可用")

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LuaScript::index,
            ByteBufCodecs.BYTE_ARRAY, LuaScript::content,
            ::LuaScript
        )
    }

}