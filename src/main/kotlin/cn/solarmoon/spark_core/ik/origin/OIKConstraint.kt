package cn.solarmoon.spark_core.ik.origin

import au.edu.federation.utils.Vec3f
import cn.solarmoon.spark_core.SparkCore
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import cn.solarmoon.spark_core.ik.component.JointConstraint
import java.util.LinkedHashMap
import java.util.Optional

/**
 * Represents a single IK constraint loaded from JSON
 */
data class OIKConstraint(
    val armatureName: String,
    val constraintTargetBone: String,
    val constraintName: String,
    val ikChainBoneLimits: LinkedHashMap<String, BoneLimit>,
    val targetObject: String? = null,
    val targetBone: String? = null,
    val poleTargetObject: String? = null,
    val poleTargetBone: String? = null,
    val chainLength: Int = 0,
    val poleAngleRad: Float? = null,
    val poleAngleDeg: Float? = null,
    val iterations: Int = 20,
    val useTail: Boolean = true,
    val useStretch: Boolean = false,
    val influence: Float = 1.0f
) {
    data class BoneLimit(
        val name: String,
        val limitRotationX: Boolean,
        val minRotationXDeg: Float?,
        val maxRotationXDeg: Float?,
        val limitRotationY: Boolean,
        val minRotationYDeg: Float?,
        val maxRotationYDeg: Float?,
        val limitRotationZ: Boolean,
        val minRotationZDeg: Float?,
        val maxRotationZDeg: Float?,
        val stiffnessX: Float,
        val stiffnessY: Float,
        val stiffnessZ: Float,
        val ikStretch: Float
    ) {
        companion object {
            val CODEC: Codec<BoneLimit> = RecordCodecBuilder.create {
                it.group(
                    Codec.STRING.fieldOf("name").forGetter { it.name },
                    Codec.BOOL.fieldOf("limit_rotation_x").forGetter { it.limitRotationX },
                    Codec.FLOAT.optionalFieldOf("min_rotation_x_deg").forGetter { Optional.ofNullable(it.minRotationXDeg) },
                    Codec.FLOAT.optionalFieldOf("max_rotation_x_deg").forGetter { Optional.ofNullable(it.maxRotationXDeg) },
                    Codec.BOOL.fieldOf("limit_rotation_y").forGetter { it.limitRotationY },
                    Codec.FLOAT.optionalFieldOf("min_rotation_y_deg").forGetter { Optional.ofNullable(it.minRotationYDeg) },
                    Codec.FLOAT.optionalFieldOf("max_rotation_y_deg").forGetter { Optional.ofNullable(it.maxRotationYDeg) },
                    Codec.BOOL.fieldOf("limit_rotation_z").forGetter { it.limitRotationZ },
                    Codec.FLOAT.optionalFieldOf("min_rotation_z_deg").forGetter { Optional.ofNullable(it.minRotationZDeg) },
                    Codec.FLOAT.optionalFieldOf("max_rotation_z_deg").forGetter { Optional.ofNullable(it.maxRotationZDeg) },
                    Codec.FLOAT.fieldOf("stiffness_x").forGetter { it.stiffnessX },
                    Codec.FLOAT.fieldOf("stiffness_y").forGetter { it.stiffnessY },
                    Codec.FLOAT.fieldOf("stiffness_z").forGetter { it.stiffnessZ },
                    Codec.FLOAT.fieldOf("ik_stretch").forGetter { it.ikStretch }
                ).apply(it) { name, limitX, minX, maxX, limitY, minY, maxY, limitZ, minZ, maxZ, stiffX, stiffY, stiffZ, stretch ->
                    BoneLimit(
                        name, limitX,
                        minX.orElse(null), maxX.orElse(null),
                        limitY,
                        minY.orElse(null), maxY.orElse(null),
                        limitZ,
                        minZ.orElse(null), maxZ.orElse(null),
                        stiffX, stiffY, stiffZ, stretch
                    )
                }
            }

            val MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC).xmap({ LinkedHashMap(it) }, { it })

            // We need to implement a custom StreamCodec for BoneLimit because it has too many fields
            // for the standard composite method
            val STREAM_CODEC = object : StreamCodec<net.minecraft.network.FriendlyByteBuf, BoneLimit> {
                override fun decode(buffer: net.minecraft.network.FriendlyByteBuf): BoneLimit {
                    val name = ByteBufCodecs.STRING_UTF8.decode(buffer)
                    val limitRotationX = ByteBufCodecs.BOOL.decode(buffer)
                    val minRotationXDeg = if (buffer.readBoolean()) buffer.readFloat() else null
                    val maxRotationXDeg = if (buffer.readBoolean()) buffer.readFloat() else null
                    val limitRotationY = ByteBufCodecs.BOOL.decode(buffer)
                    val minRotationYDeg = if (buffer.readBoolean()) buffer.readFloat() else null
                    val maxRotationYDeg = if (buffer.readBoolean()) buffer.readFloat() else null
                    val limitRotationZ = ByteBufCodecs.BOOL.decode(buffer)
                    val minRotationZDeg = if (buffer.readBoolean()) buffer.readFloat() else null
                    val maxRotationZDeg = if (buffer.readBoolean()) buffer.readFloat() else null
                    val stiffnessX = ByteBufCodecs.FLOAT.decode(buffer)
                    val stiffnessY = ByteBufCodecs.FLOAT.decode(buffer)
                    val stiffnessZ = ByteBufCodecs.FLOAT.decode(buffer)
                    val ikStretch = ByteBufCodecs.FLOAT.decode(buffer)

                    return BoneLimit(
                        name, limitRotationX, minRotationXDeg, maxRotationXDeg,
                        limitRotationY, minRotationYDeg, maxRotationYDeg,
                        limitRotationZ, minRotationZDeg, maxRotationZDeg,
                        stiffnessX, stiffnessY, stiffnessZ, ikStretch
                    )
                }

                override fun encode(buffer: net.minecraft.network.FriendlyByteBuf, value: BoneLimit) {
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.name)
                    ByteBufCodecs.BOOL.encode(buffer, value.limitRotationX)
                    buffer.writeBoolean(value.minRotationXDeg != null)
                    if (value.minRotationXDeg != null) buffer.writeFloat(value.minRotationXDeg)
                    buffer.writeBoolean(value.maxRotationXDeg != null)
                    if (value.maxRotationXDeg != null) buffer.writeFloat(value.maxRotationXDeg)
                    ByteBufCodecs.BOOL.encode(buffer, value.limitRotationY)
                    buffer.writeBoolean(value.minRotationYDeg != null)
                    if (value.minRotationYDeg != null) buffer.writeFloat(value.minRotationYDeg)
                    buffer.writeBoolean(value.maxRotationYDeg != null)
                    if (value.maxRotationYDeg != null) buffer.writeFloat(value.maxRotationYDeg)
                    ByteBufCodecs.BOOL.encode(buffer, value.limitRotationZ)
                    buffer.writeBoolean(value.minRotationZDeg != null)
                    if (value.minRotationZDeg != null) buffer.writeFloat(value.minRotationZDeg)
                    buffer.writeBoolean(value.maxRotationZDeg != null)
                    if (value.maxRotationZDeg != null) buffer.writeFloat(value.maxRotationZDeg)
                    ByteBufCodecs.FLOAT.encode(buffer, value.stiffnessX)
                    ByteBufCodecs.FLOAT.encode(buffer, value.stiffnessY)
                    ByteBufCodecs.FLOAT.encode(buffer, value.stiffnessZ)
                    ByteBufCodecs.FLOAT.encode(buffer, value.ikStretch)
                }
            }

            val MAP_STREAM_CODEC = object : StreamCodec<net.minecraft.network.FriendlyByteBuf, LinkedHashMap<String, BoneLimit>> {
                override fun decode(buffer: net.minecraft.network.FriendlyByteBuf): LinkedHashMap<String, BoneLimit> {
                    val size = buffer.readVarInt()
                    val map = LinkedHashMap<String, BoneLimit>(size)

                    for (i in 0 until size) {
                        val key = ByteBufCodecs.STRING_UTF8.decode(buffer)
                        val value = STREAM_CODEC.decode(buffer)
                        map[key] = value
                    }

                    return map
                }

                override fun encode(buffer: net.minecraft.network.FriendlyByteBuf, value: LinkedHashMap<String, BoneLimit>) {
                    buffer.writeVarInt(value.size)
                    value.forEach { (key, boneLimit) ->
                        ByteBufCodecs.STRING_UTF8.encode(buffer, key)
                        STREAM_CODEC.encode(buffer, boneLimit)
                    }
                }
            }
        }

        fun toJointConstraint(): JointConstraint? {
            // Convert BoneLimit to appropriate JointConstraint
            if (limitRotationX || limitRotationY || limitRotationZ) {
                // For simplicity, we'll convert to a hinge constraint if any axis is limited
                if (limitRotationX && minRotationXDeg != null && maxRotationXDeg != null) {
                    return JointConstraint.Hinge(
                        Vec3f(1f, 0f, 0f), // X-axis rotation
                        minRotationXDeg,
                        maxRotationXDeg,
                        Vec3f(0f, 1f, 0f) // Reference axis
                    )
                } else if (limitRotationY && minRotationYDeg != null && maxRotationYDeg != null) {
                    return JointConstraint.Hinge(
                        Vec3f(0f, 1f, 0f), // Y-axis rotation
                        minRotationYDeg,
                        maxRotationYDeg,
                        Vec3f(1f, 0f, 0f) // Reference axis
                    )
                } else if (limitRotationZ && minRotationZDeg != null && maxRotationZDeg != null) {
                    return JointConstraint.Hinge(
                        Vec3f(0f, 0f, 1f), // Z-axis rotation
                        minRotationZDeg,
                        maxRotationZDeg,
                        Vec3f(1f, 0f, 0f) // Reference axis
                    )
                }
            }
            return null
        }
    }

    companion object {
        /**
         * Get an IK constraint by its ID. Returns EMPTY if not found.
         */
        @JvmStatic
        fun get(id: ResourceLocation) = ORIGINS[id] ?: EMPTY

        /**
         * Map of all loaded IK constraints, populated by the IKConstraintListener.
         * Key is the ResourceLocation ID, value is the OIKConstraint.
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, OIKConstraint>()

        /**
         * Empty IK constraint for safe fallback
         */
        @JvmStatic
        val EMPTY = OIKConstraint("", "", "", linkedMapOf(), null, null, null, null, 0, null, null, 20, true, false, 1.0f)

        @JvmStatic
        val CODEC: Codec<OIKConstraint> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("armature_name").forGetter { it.armatureName },
                Codec.STRING.fieldOf("constraint_target_bone").forGetter { it.constraintTargetBone },
                Codec.STRING.fieldOf("constraint_name").forGetter { it.constraintName },
                BoneLimit.MAP_CODEC.fieldOf("ik_chain_bone_limits").forGetter { it.ikChainBoneLimits },
                Codec.STRING.optionalFieldOf("target_object").forGetter { Optional.ofNullable(it.targetObject) },
                Codec.STRING.optionalFieldOf("target_bone").forGetter { Optional.ofNullable(it.targetBone) },
                Codec.STRING.optionalFieldOf("pole_target_object").forGetter { Optional.ofNullable(it.poleTargetObject) },
                Codec.STRING.optionalFieldOf("pole_target_bone").forGetter { Optional.ofNullable(it.poleTargetBone) },
                Codec.INT.optionalFieldOf("chain_length", 0).forGetter { it.chainLength },
                Codec.FLOAT.optionalFieldOf("pole_angle_rad").forGetter { Optional.ofNullable(it.poleAngleRad) },
                Codec.FLOAT.optionalFieldOf("pole_angle_deg").forGetter { Optional.ofNullable(it.poleAngleDeg) },
                Codec.INT.optionalFieldOf("iterations", 20).forGetter { it.iterations },
                Codec.BOOL.optionalFieldOf("use_tail", true).forGetter { it.useTail },
                Codec.BOOL.optionalFieldOf("use_stretch", false).forGetter { it.useStretch },
                Codec.FLOAT.optionalFieldOf("influence", 1.0f).forGetter { it.influence }
            ).apply(it) { armatureName, constraintTargetBone, constraintName, boneLimits, targetObject, targetBone,
                      poleTargetObject, poleTargetBone, chainLength, poleAngleRad, poleAngleDeg, iterations, useTail, useStretch, influence ->
                OIKConstraint(
                    armatureName, constraintTargetBone, constraintName, boneLimits,
                    targetObject.orElse(null), targetBone.orElse(null),
                    poleTargetObject.orElse(null), poleTargetBone.orElse(null),
                    chainLength, poleAngleRad.orElse(null), poleAngleDeg.orElse(null),
                    iterations, useTail, useStretch, influence
                )
            }
        }

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<net.minecraft.network.FriendlyByteBuf, OIKConstraint> {
            override fun decode(buffer: net.minecraft.network.FriendlyByteBuf): OIKConstraint {
                val armatureName = ByteBufCodecs.STRING_UTF8.decode(buffer)
                val constraintTargetBone = ByteBufCodecs.STRING_UTF8.decode(buffer)
                val constraintName = ByteBufCodecs.STRING_UTF8.decode(buffer)
                val ikChainBoneLimits = BoneLimit.MAP_STREAM_CODEC.decode(buffer)

                val hasTargetObject = buffer.readBoolean()
                val targetObject = if (hasTargetObject) ByteBufCodecs.STRING_UTF8.decode(buffer) else null

                val hasTargetBone = buffer.readBoolean()
                val targetBone = if (hasTargetBone) ByteBufCodecs.STRING_UTF8.decode(buffer) else null

                val hasPoleTargetObject = buffer.readBoolean()
                val poleTargetObject = if (hasPoleTargetObject) ByteBufCodecs.STRING_UTF8.decode(buffer) else null

                val hasPoleTargetBone = buffer.readBoolean()
                val poleTargetBone = if (hasPoleTargetBone) ByteBufCodecs.STRING_UTF8.decode(buffer) else null

                val chainLength = buffer.readInt()

                val hasPoleAngleRad = buffer.readBoolean()
                val poleAngleRad = if (hasPoleAngleRad) buffer.readFloat() else null

                val hasPoleAngleDeg = buffer.readBoolean()
                val poleAngleDeg = if (hasPoleAngleDeg) buffer.readFloat() else null

                val iterations = buffer.readInt()
                val useTail = buffer.readBoolean()
                val useStretch = buffer.readBoolean()
                val influence = buffer.readFloat()

                return OIKConstraint(
                    armatureName, constraintTargetBone, constraintName, ikChainBoneLimits,
                    targetObject, targetBone, poleTargetObject, poleTargetBone,
                    chainLength, poleAngleRad, poleAngleDeg, iterations, useTail, useStretch, influence
                )
            }

            override fun encode(buffer: net.minecraft.network.FriendlyByteBuf, value: OIKConstraint) {
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.armatureName)
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.constraintTargetBone)
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.constraintName)
                BoneLimit.MAP_STREAM_CODEC.encode(buffer, value.ikChainBoneLimits)

                buffer.writeBoolean(value.targetObject != null)
                if (value.targetObject != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.targetObject)

                buffer.writeBoolean(value.targetBone != null)
                if (value.targetBone != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.targetBone)

                buffer.writeBoolean(value.poleTargetObject != null)
                if (value.poleTargetObject != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.poleTargetObject)

                buffer.writeBoolean(value.poleTargetBone != null)
                if (value.poleTargetBone != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.poleTargetBone)

                buffer.writeInt(value.chainLength)

                buffer.writeBoolean(value.poleAngleRad != null)
                if (value.poleAngleRad != null) buffer.writeFloat(value.poleAngleRad)

                buffer.writeBoolean(value.poleAngleDeg != null)
                if (value.poleAngleDeg != null) buffer.writeFloat(value.poleAngleDeg)

                buffer.writeInt(value.iterations)
                buffer.writeBoolean(value.useTail)
                buffer.writeBoolean(value.useStretch)
                buffer.writeFloat(value.influence)
            }
        }

        @JvmStatic
        val LIST_CODEC = Codec.list(CODEC)

        @JvmStatic
        val LIST_STREAM_CODEC = object : StreamCodec<net.minecraft.network.FriendlyByteBuf, List<OIKConstraint>> {
            override fun decode(buffer: net.minecraft.network.FriendlyByteBuf): List<OIKConstraint> {
                val size = buffer.readVarInt()
                val list = ArrayList<OIKConstraint>(size)

                for (i in 0 until size) {
                    list.add(STREAM_CODEC.decode(buffer))
                }

                return list
            }

            override fun encode(buffer: net.minecraft.network.FriendlyByteBuf, value: List<OIKConstraint>) {
                buffer.writeVarInt(value.size)
                value.forEach { constraint ->
                    STREAM_CODEC.encode(buffer, constraint)
                }
            }
        }

        /**
         * Codec for serializing a map of ResourceLocation to OIKConstraint
         */
        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = object : StreamCodec<net.minecraft.network.FriendlyByteBuf, LinkedHashMap<ResourceLocation, OIKConstraint>> {
            override fun decode(buffer: net.minecraft.network.FriendlyByteBuf): LinkedHashMap<ResourceLocation, OIKConstraint> {
                val size = buffer.readVarInt()
                val map = LinkedHashMap<ResourceLocation, OIKConstraint>(size)

                for (i in 0 until size) {
                    val key = ResourceLocation.STREAM_CODEC.decode(buffer)
                    val value = STREAM_CODEC.decode(buffer)
                    map[key] = value
                }

                return map
            }

            override fun encode(buffer: net.minecraft.network.FriendlyByteBuf, value: LinkedHashMap<ResourceLocation, OIKConstraint>) {
                buffer.writeVarInt(value.size)

                value.forEach { (key, value) ->
                    ResourceLocation.STREAM_CODEC.encode(buffer, key)
                    STREAM_CODEC.encode(buffer, value)
                }
            }
        }
    }
}
