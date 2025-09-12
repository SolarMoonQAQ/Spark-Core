package cn.solarmoon.spark_core.resource2.graph

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

data class SparkPackage(
    val meta: SparkPackMetaInfo,
    val entries: MutableMap<String, MutableMap<String, ByteArray>> // parentKey = 模块名, key = 文件内路径, value = 文件内容
) {

    val modules get() = entries.keys

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            SparkPackMetaInfo.STREAM_CODEC, SparkPackage::meta,
            ByteBufCodecs.map(
                ::LinkedHashMap,
                ByteBufCodecs.STRING_UTF8,
                ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.BYTE_ARRAY)
            ), SparkPackage::entries,
            ::SparkPackage
        )

        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list())
    }

}
