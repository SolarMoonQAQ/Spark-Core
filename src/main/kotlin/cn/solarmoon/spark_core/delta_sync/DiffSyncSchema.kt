package cn.solarmoon.spark_core.delta_sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries

/**
 * 差异同步规则：保存字段列表并提供默认差异检测
 */
class DiffSyncSchema<D: Any>(
    val fields: List<FieldDef<*, D>>
) {
    val id get() = SparkRegistries.DIFF_SYNC_SCHEMA.getId(this)

    fun diffMask(oldSnapshot: DiffSnapshot, newObj: D): Long {
        var mask = 0L
        for (field in fields) {
            val oldVal = oldSnapshot.values[field.maskBit]
            val newVal = field.extract(newObj)
            val checker = field.hasChanged as (Any?, Any?) -> Boolean
            if (checker(oldVal, newVal)) {
                mask = mask or field.maskBit
            }
        }
        return mask
    }

    fun diffPacket(oldSnapshot: DiffSnapshot, newObj: D): DiffPacket {
        var mask = 0L
        var nullMask = 0L
        val values = mutableListOf<Any?>()

        for (field in fields) {
            val oldVal = oldSnapshot.values[field.maskBit]
            val newVal = field.extract(newObj)
            val checker = field.hasChanged as (Any?, Any?) -> Boolean
            if (checker(oldVal, newVal)) {
                mask = mask or field.maskBit
                if (newVal == null) {
                    nullMask = nullMask or field.maskBit
                    values.add(null)
                } else {
                    values.add(newVal)
                }
            }
        }

        if (mask == 0L) return DiffPacket(id.toShort(), 0, 0, emptyList())
        return DiffPacket(id.toShort(), mask, nullMask, values)
    }

    // 应用 diff 到对象，并更新快照
    fun applyDiff(diff: DiffPacket, target: D) {
        var index = 0
        for (field in fields) {
            if ((diff.mask and field.maskBit) != 0L) {
                val isNull = (diff.nullMask and field.maskBit) != 0L
                val value = diff.values[index++]
                if (!isNull && value != null) {
                    (field.apply as (D, Any) -> Unit).invoke(target, value)
                }
            }
        }
    }

    // 从对象生成快照
    fun snapshotFrom(obj: D): DiffSnapshot {
        val snap = mutableMapOf<Long, Any?>()
        for (field in fields) {
            snap[field.maskBit] = field.extract(obj)
        }
        return DiffSnapshot(snap)
    }

}

