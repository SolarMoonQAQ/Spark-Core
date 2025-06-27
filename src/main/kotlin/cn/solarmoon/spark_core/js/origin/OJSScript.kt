package cn.solarmoon.spark_core.js.origin

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * JS脚本数据类
 * 用于动态注册表管理
 */
data class OJSScript(
    val apiId: String,          // API模块ID
    val fileName: String,       // 脚本文件名
    val content: String,        // 脚本内容
    val location: ResourceLocation  // 资源位置标识
) {
    companion object {
        
        /**
         * 获取JS脚本。
         * 统一数据访问优先级：SparkRegistries动态注册表 > 静态ORIGINS
         * 确保客户端和服务端数据一致性
         */
        @JvmStatic
        fun get(res: ResourceLocation): OJSScript? {
            // 优先从动态注册表获取
            SparkRegistries.JS_SCRIPTS?.let { registry ->
                val resourceKey = net.minecraft.resources.ResourceKey.create(registry.key(), res)
                registry.get(resourceKey)?.let { return it }
            }
            
            // 回退到静态ORIGINS
            return ORIGINS[res]
        }

        /**
         * 根据API ID和文件名获取JS脚本
         */
        @JvmStatic
        fun get(apiId: String, fileName: String): OJSScript? {
            // 优先从动态注册表获取
            SparkRegistries.JS_SCRIPTS?.let { registry ->
                registry.entrySet().forEach { entry ->
                    val script = entry.value
                    if (script.apiId == apiId && script.fileName == fileName) {
                        return script
                    }
                }
            }
            
            // 回退到静态ORIGINS
            return ORIGINS.values.firstOrNull { it.apiId == apiId && it.fileName == fileName }
        }

        /**
         * 根据API ID获取所有相关的JS脚本
         */
        @JvmStatic
        fun getByApiId(apiId: String): List<OJSScript> {
            val result = mutableListOf<OJSScript>()
            
            // 从动态注册表获取
            SparkRegistries.JS_SCRIPTS?.let { registry ->
                registry.entrySet().forEach { entry ->
                    val script = entry.value
                    if (script.apiId == apiId) {
                        result.add(script)
                    }
                }
            }
            
            // 如果动态注册表为空或未找到，从静态ORIGINS获取
            if (result.isEmpty()) {
                result.addAll(ORIGINS.values.filter { it.apiId == apiId })
            }
            
            return result
        }

        @JvmStatic
        var ORIGINS = linkedMapOf<ResourceLocation, OJSScript>()

        @JvmField
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OJSScript> {
            override fun encode(buf: FriendlyByteBuf, script: OJSScript) {
                buf.writeUtf(script.apiId)
                buf.writeUtf(script.fileName)
                buf.writeUtf(script.content)
                buf.writeResourceLocation(script.location)
            }

            override fun decode(buf: FriendlyByteBuf): OJSScript {
                val apiId = buf.readUtf()
                val fileName = buf.readUtf()
                val content = buf.readUtf()
                val location = buf.readResourceLocation()
                return OJSScript(apiId, fileName, content, location)
            }
        }
    }
}