package cn.solarmoon.spark_core.delta_sync

import cn.solarmoon.spark_core.util.CodecRegistry
import cn.solarmoon.spark_core.util.MaskAllocator
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiffSyncField(val fieldFirst: Boolean = false)

inline fun <reified D : Any> generateSchema(id: ResourceLocation) = generateSchema(D::class, id)

fun <D : Any> generateSchema(type: KClass<D>, id: ResourceLocation): DiffSyncSchema<D> {
    val allocator = MaskAllocator.byId(id)
    val fields = type.memberProperties.mapNotNull { prop ->
        val ann = prop.findAnnotation<DiffSyncField>() ?: return@mapNotNull null
        val type = prop.returnType.classifier as? KClass<*> ?: error("无法获取属性 ${prop.name} 的类型")

        val field = prop.javaField
        FieldDef<Any?, D>(
            maskBit = allocator.nextMask(),
            codec = CodecRegistry.get(type) as StreamCodec<ByteBuf, Any?>,
            apply = { target, value ->
                if (ann.fieldFirst && field != null) {
                    field.isAccessible = true
                    field.set(target, value)
                } else if (prop as? KMutableProperty1<D, Any?> != null) {
                    prop.set(target, value)
                }
            },
            extract = { source -> prop.get(source) }
        )
    }

    return DiffSyncSchema(fields)
}
