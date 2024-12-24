package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimationSet
import cn.solarmoon.spark_core.animation.anim.part.Animation
import cn.solarmoon.spark_core.animation.anim.part.Loop
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.phys.thread.PhysLevel
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 混合动画的基本单元
 * @param startTransSpeed 从当前动画过渡到该动画所需时间，过渡和动画的播放是独立的，因此必须等待过渡完成后动画才会正式开始播放
 * @param endTransSpeed 大部分情况下，为了过渡的平滑，动画的过渡都以即将开始的动画的最慢的[startTransSpeed]为准，此值在混合过渡或是其它特殊情况下会有用
 * @param level level为0时，动画作为主级动画而存在，会在各种判断中被优先判断，而其它level值则皆为次级动画，不会被默认的get获取。
 * 它适用于例如：防守动画混合了一个走路动画，此时如果结束防守，在状态动画中会认为走路动画仍在继续而不进行走路动画的过渡，实际上在这种情况下，走路动画只是作为防守动画的混合次级动画而存在，不应当
 * 参与是否正在走路的判据，因此将他的level设为别的值，将这里次级动画的各种播放判断和主级动画分开，这样就能互不影响的正常播放各自动画。当然，如果正常混合没有出现问题，则此值最好不要改变。
 */
data class MixedAnimation(
    var modelPath: ResourceLocation,
    val name: String,
    var weight: Int = 1,
    private var _speed: Float = 1f,
    private var _startTransSpeed: Float = 1f,
    var endTransSpeed: Float = 1f,
    var level: Int = 0
) {
    constructor(animatable: IAnimatable<*>, animName: String, weight: Int = 1, speed: Float = 1f, startTransSpeed: Float = 1f, endTransSpeed: Float = 1f, level: Int = 0):
            this(animatable.animData.modelPath, animName, weight, speed, startTransSpeed, endTransSpeed, level)

    /**
     * 只是引用动画内容时才可用此构造函数
     */
    constructor(animName: String, weight: Int = 1, speed: Float = 1f, startTransSpeed: Float = 1f, endTransSpeed: Float = 1f, level: Int = 0):
            this(ResourceLocation.withDefaultNamespace("player"), animName, weight, speed, startTransSpeed, endTransSpeed, level)

    var startTransSpeed get() = _startTransSpeed
        set(value) {
            _startTransSpeed = value
            if (value > MAX_TRANS_PROCESS) transTick = MAX_TRANS_PROCESS
            else if (transTick >= MAX_TRANS_PROCESS) transTick = 0.0
        }
    val animation get() = AnimationSet.get(modelPath).getAnimation(name) ?: Animation(name, Loop.TRUE, 0.0, arrayListOf())
    var tick = 0.0
    var transTick = 0.0
    var isCancelled = false
    /**
     * 不参与混合的骨骼，存在在此列表的骨骼将不会应用该动画的变换
     */
    var boneBlacklist = mutableSetOf<String>()

    var freeze = 1f

    init {
        if (startTransSpeed > MAX_TRANS_PROCESS) transTick = MAX_TRANS_PROCESS
    }

    val maxTick get() = animation.baseLifeTime * TICKS_PRE_SECOND
    var speed
        get() = _speed * freeze.toFloat()
        set(value) { _speed = value }
    val shouldForwardTransition get() = transTick < MAX_TRANS_PROCESS && !isCancelled
    val shouldBackwardTransition get() = transTick > 0 && isCancelled
    val isInTransition get() = shouldForwardTransition || shouldBackwardTransition

    /**
     * 获取带过渡的权重
     */
    fun getWeight(partialTicks: Float = 0f): Float = weight * getTransProgress(partialTicks)

    fun getTransProgress(partialTicks: Float = 0f): Float {
        val partialTicks = if (transTick <= 0 && isCancelled) 0.00001f else partialTicks
        val partial = if (shouldBackwardTransition) -partialTicks * endTransSpeed else if (shouldForwardTransition) partialTicks * startTransSpeed else partialTicks
        val progress = (transTick + partial) / MAX_TRANS_PROCESS
        return progress.toFloat().coerceIn(0f, 1f)
    }

    fun getProgress(partialTicks: Float = 0f): Float = (tick.toFloat() + partialTicks) / maxTick.toFloat()

    /**
     * 判断tick是否在两个时间段之间，此方法会考虑到动画速度的影响和tick本身的时间间隔，因此只需填入动画里默认的时间段即可
     */
    fun isTickIn(time1: Double, time2: Double): Boolean {
        return tick in time1 * TICKS_PRE_SECOND .. time2 * TICKS_PRE_SECOND
    }

    // 比较名字保证只有一种该动画，同时比较cancelled保证同种动画的切换之间存在过渡
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MixedAnimation) return false

        return modelPath == other.modelPath && name == other.name && isCancelled == other.isCancelled && level == other.level
    }

    override fun hashCode(): Int {
        return 31 * modelPath.hashCode() + name.hashCode() + isCancelled.hashCode() + level.hashCode()
    }

    fun copy(): MixedAnimation {
        val anim = MixedAnimation(modelPath, name, weight, speed, startTransSpeed, endTransSpeed, level)
        anim.tick = tick
        anim.transTick = transTick
        anim.isCancelled = isCancelled
        anim.boneBlacklist = boneBlacklist
        return anim
    }

    companion object {
        /**
         * 过渡的最大tick长度，过渡时会从当前过渡tick值每tick递增当前速度值，速度为1则从0抵达这个值需要4tick，速度为0.5则需要2tick，以此类推
         *
         * 同时他也意味着如果使用默认的过渡速度，则过渡时间为4tick
         */
        @JvmStatic
        val MAX_TRANS_PROCESS get() = 5.0

        @JvmStatic
        val TICKS_PRE_SECOND = 20

        @JvmStatic
        val CODEC: Codec<MixedAnimation> = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("model_path").forGetter { it.modelPath },
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.INT.fieldOf("weight").forGetter { it.weight },
                Codec.FLOAT.fieldOf("speed").forGetter { it.speed },
                Codec.FLOAT.fieldOf("start_transpeed").forGetter { it.startTransSpeed },
                Codec.FLOAT.fieldOf("end_transpeed").forGetter { it.endTransSpeed },
                Codec.INT.fieldOf("level").forGetter { it.level },
                Codec.DOUBLE.fieldOf("tick").forGetter { it.tick },
                Codec.DOUBLE.fieldOf("transtick").forGetter { it.transTick },
                Codec.BOOL.fieldOf("cancelled").forGetter { it.isCancelled },
                Codec.STRING.listOf().fieldOf("blacklist_bones").forGetter { it.boneBlacklist.toList() },
            ).apply(it) { w,a,b,c,d,e, ha, f,g,h,i -> MixedAnimation(w, a, b, c, d, e, ha).apply { tick = f; transTick = g; isCancelled = h; boneBlacklist = i.toMutableSet()} }
        }

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, MixedAnimation> {
            override fun decode(buffer: RegistryFriendlyByteBuf): MixedAnimation {
                val path = ResourceLocation.STREAM_CODEC.decode(buffer)
                val name = buffer.readUtf()
                val weight = buffer.readInt()
                val speed = buffer.readFloat()
                val startTransSpeed = buffer.readFloat()
                val endTransSpeed = buffer.readFloat()
                val level = buffer.readInt()
                val tick = buffer.readDouble()
                val transTick = buffer.readDouble()
                val enabled = buffer.readBoolean()
                val targetBones = SerializeHelper.STRING_SET_STREAM_CODEC.decode(buffer)
                return MixedAnimation(path, name, weight, speed, startTransSpeed, endTransSpeed, level).apply { this.tick = tick; this.transTick = transTick; isCancelled = enabled; boneBlacklist = targetBones}
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: MixedAnimation) {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.modelPath)
                buffer.writeUtf(value.name)
                buffer.writeInt(value.weight)
                buffer.writeFloat(value.speed)
                buffer.writeFloat(value.startTransSpeed)
                buffer.writeFloat(value.endTransSpeed)
                buffer.writeInt(value.level)
                buffer.writeDouble(value.tick)
                buffer.writeDouble(value.transTick)
                buffer.writeBoolean(value.isCancelled)
                SerializeHelper.STRING_SET_STREAM_CODEC.encode(buffer, value.boneBlacklist)
            }
        }

        @JvmStatic
        val SET_CODEC = Codec.list(CODEC).xmap(
            { LinkedHashSet(it) },
            { it.toList() }
        )

        @JvmStatic
        val SET_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { linkedSetOf() })

        @JvmStatic
        val LIST_CODEC = CODEC.listOf().xmap(
            { ConcurrentLinkedQueue(it) },
            { it.toList() }
        )

        @JvmStatic
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { ConcurrentLinkedQueue() })
    }

}
