package cn.solarmoon.spark_core.resource2.graph

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

data class SparkPackage(
    val meta: SparkPackMetaInfo,
    val entries: Map<String, ByteArray> // key = zip 内路径, value = 文件内容
) {

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            SparkPackMetaInfo.STREAM_CODEC, SparkPackage::meta,
            ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.BYTE_ARRAY), SparkPackage::entries,
            ::SparkPackage
        )
    }

}
