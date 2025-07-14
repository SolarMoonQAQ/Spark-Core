package cn.solarmoon.spark_core.resource.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import java.util.Optional

/**
 * 依赖解析结果
 * 支持序列化的依赖解析结果，用于网络传输和存储
 */
sealed class DependencyResolution {
    /** 解析成功 */
    data class Success(val resource: Any) : DependencyResolution()

    /** 解析失败 */
    data class Missing(val dependency: OResourceDependency, val reason: String) : DependencyResolution()

    /** 版本不兼容 */
    data class VersionMismatch(val dependency: OResourceDependency, val actual: String?, val expected: String) : DependencyResolution()

    /**
     * 检查解析结果是否成功
     */
    val isSuccess: Boolean
        get() = this is Success
    
    companion object {
        /**
         * Mojang Codec for DependencyResolution
         */
        @JvmStatic
        val CODEC: Codec<DependencyResolution> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("type").forGetter { 
                    when (it) {
                        is Success -> "success"
                        is Missing -> "missing"
                        is VersionMismatch -> "version_mismatch"
                    }
                },
                Codec.STRING.optionalFieldOf("reason").forGetter { 
                    when (it) {
                        is Missing -> Optional.of(it.reason)
                        is VersionMismatch -> Optional.of(it.expected)
                        else -> Optional.empty()
                    }
                },
                Codec.STRING.optionalFieldOf("actual").forGetter { 
                    when (it) {
                        is VersionMismatch -> Optional.ofNullable(it.actual)
                        else -> Optional.empty()
                    }
                },
                OResourceDependency.CODEC.optionalFieldOf("dependency").forGetter { 
                    when (it) {
                        is Missing -> Optional.of(it.dependency)
                        is VersionMismatch -> Optional.of(it.dependency)
                        else -> Optional.empty()
                    }
                },
                Codec.STRING.optionalFieldOf("resource").forGetter { 
                    when (it) {
                        is Success -> Optional.of(it.resource.toString())
                        else -> Optional.empty()
                    }
                }
            ).apply(instance) { type, reason, actual, dependency, resource ->
                when (type) {
                    "success" -> Success(resource.orElse("unknown"))
                    "missing" -> Missing(
                        dependency.orElse(OResourceDependency(
                            id = net.minecraft.resources.ResourceLocation.parse("unknown:unknown"),
                            type = ODependencyType.HARD,
                            path = "unknown"
                        )), 
                        reason.orElse("unknown")
                    )
                    "version_mismatch" -> VersionMismatch(
                        dependency.orElse(OResourceDependency(
                            id = net.minecraft.resources.ResourceLocation.parse("unknown:unknown"),
                            type = ODependencyType.HARD,
                            path = "unknown"
                        )), 
                        actual.orElse(null), 
                        reason.orElse("unknown")
                    )
                    else -> Missing(OResourceDependency(
                        id = net.minecraft.resources.ResourceLocation.parse("unknown:unknown"),
                        type = ODependencyType.HARD,
                        path = "unknown"
                    ), "unknown resolution type")
                }
            }
        }
        
        /**
         * Stream codec for network serialization
         */
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, DependencyResolution> {
            override fun decode(buffer: FriendlyByteBuf): DependencyResolution {
                val type = buffer.readUtf()
                return when (type) {
                    "success" -> {
                        val resource = buffer.readUtf()
                        Success(resource)
                    }
                    "missing" -> {
                        val dependency = OResourceDependency.STREAM_CODEC.decode(buffer)
                        val reason = buffer.readUtf()
                        Missing(dependency, reason)
                    }
                    "version_mismatch" -> {
                        val dependency = OResourceDependency.STREAM_CODEC.decode(buffer)
                        val actual = if (buffer.readBoolean()) buffer.readUtf() else null
                        val expected = buffer.readUtf()
                        VersionMismatch(dependency, actual, expected)
                    }
                    else -> Missing(OResourceDependency(
                        id = net.minecraft.resources.ResourceLocation.parse("unknown:unknown"),
                        type = ODependencyType.HARD,
                        path = "unknown"
                    ), "unknown resolution type")
                }
            }

            override fun encode(buffer: FriendlyByteBuf, value: DependencyResolution) {
                when (value) {
                    is Success -> {
                        buffer.writeUtf("success")
                        buffer.writeUtf(value.resource.toString())
                    }
                    is Missing -> {
                        buffer.writeUtf("missing")
                        OResourceDependency.STREAM_CODEC.encode(buffer, value.dependency)
                        buffer.writeUtf(value.reason)
                    }
                    is VersionMismatch -> {
                        buffer.writeUtf("version_mismatch")
                        OResourceDependency.STREAM_CODEC.encode(buffer, value.dependency)
                        buffer.writeBoolean(value.actual != null)
                        value.actual?.let { buffer.writeUtf(it) }
                        buffer.writeUtf(value.expected)
                    }
                }
            }
        }
        
        /**
         * List codec for dependency resolution collections
         */
        @JvmStatic
        val LIST_CODEC: Codec<List<DependencyResolution>> = CODEC.listOf()
        
        /**
         * Stream codec for dependency resolution lists
         */
        @JvmStatic
        val LIST_STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, List<DependencyResolution>> {
            override fun decode(buffer: FriendlyByteBuf): List<DependencyResolution> {
                val size = buffer.readVarInt()
                val list = ArrayList<DependencyResolution>(size)
                for (i in 0 until size) {
                    list.add(STREAM_CODEC.decode(buffer))
                }
                return list
            }

            override fun encode(buffer: FriendlyByteBuf, value: List<DependencyResolution>) {
                buffer.writeVarInt(value.size)
                for (item in value) {
                    STREAM_CODEC.encode(buffer, item)
                }
            }
        }
    }
}