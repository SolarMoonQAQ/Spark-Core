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
        val mask = diffMask(oldSnapshot, newObj)
        if (mask == 0L) return DiffPacket(id, 0, emptyMap())

        val changes = mutableMapOf<Long, Any?>()
        for (field in fields) {
            if ((mask and field.maskBit) != 0L) {
                changes[field.maskBit] = field.extract(newObj)
            }
        }
        return DiffPacket(id, mask, changes)
    }

    // 从对象生成快照
    fun snapshotFrom(obj: D): DiffSnapshot {
        val snap = mutableMapOf<Long, Any?>()
        for (field in fields) {
            snap[field.maskBit] = field.extract(obj)
        }
        return DiffSnapshot(snap)
    }

    // 应用 diff 到对象，并更新快照
    fun applyDiff(diff: DiffPacket, target: D) {
        for (field in fields) {
            if ((diff.mask and field.maskBit) != 0L && diff.changes.containsKey(field.maskBit)) {
                val value = diff.changes[field.maskBit]!!
                (field.apply as (D, Any) -> Unit).invoke(target, value)
            }
        }
    }

}

