package cn.solarmoon.spark_core.entity.attack

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.sync.AttackSystemSyncPayload
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.fml.ModList

/**
 * 统一的攻击方法，方便对攻击数据进行统一修改
 */
class AttackSystem {
    private var associatedEntity: Entity? = null
    private var collisionBoxId: String? = null
    private var autoResetEnabled: Boolean = false
    private var cbName: String = ""
    /**
     * 初始化此 AttackSystem 的上下文，用于后续的同步操作。
     * @param entity 与此 AttackSystem 关联的实体。
     * @param cbId 相关的碰撞体 ID。
     */
    fun initializeContext(entity: Entity, cbId: String, cbName: String, resetAfterTicks: Int = 0, autoResetEnabled: Boolean = false) {
        this.associatedEntity = entity
        this.collisionBoxId = cbId
        this.cbName = cbName
        this.autoResetEnabled = autoResetEnabled
        this.resetAfterTicks = resetAfterTicks
        // 初始化时也发送一次，确保客户端状态正确
        trySendSyncPacket()
    }

    private var _attackedEntities = mutableSetOf<Int>()

    /**
     * 单次攻击后，攻击过的生物将存入此列表，并不再触发攻击，直到调用[reset]为止
     */
    val attackedEntities: MutableSet<Int> = object : MutableSet<Int> by _attackedEntities {
        override fun add(element: Int): Boolean {
            val result = _attackedEntities.add(element)
            if (result) {
                trySendSyncPacket()
            }
            return result
        }

        override fun addAll(elements: Collection<Int>): Boolean {
            val result = _attackedEntities.addAll(elements)
            if (result) {
                trySendSyncPacket()
            }
            return result
        }

        override fun clear() {
            val changed = _attackedEntities.isNotEmpty()
            _attackedEntities.clear()
            if (changed) {
                trySendSyncPacket()
            }
        }

        override fun remove(element: Int): Boolean {
            val result = _attackedEntities.remove(element)
            if (result) {
                trySendSyncPacket()
            }
            return result
        }

        override fun removeAll(elements: Collection<Int>): Boolean {
            val result = _attackedEntities.removeAll(elements.toSet()) // 确保是 Set 以优化性能
            if (result) {
                trySendSyncPacket()
            }
            return result
        }

        override fun retainAll(elements: Collection<Int>): Boolean {
            val result = _attackedEntities.retainAll(elements.toSet()) // 确保是 Set 以优化性能
            if (result) {
                trySendSyncPacket()
            }
            return result
        }
    }

    internal fun internalSetAttackedEntities(newAttackedEntities: Set<Int>) {
        // This method is called on the client side to update its state from the server.
        // It should not trigger a new sync packet back to the server.
        if (_attackedEntities != newAttackedEntities) {
            _attackedEntities = newAttackedEntities.toMutableSet()
            // If there's any client-side logic that needs to react to this change, trigger it here.
            // For example, if UI elements depend on this state.
        }
    }

    /**
     * 是否忽略目标无敌时间
     * @see attackedEntities
     */
    var ignoreInvulnerableTime = true
        set(value) {
            if (field != value) {
                field = value
                trySendSyncPacket()
            }
        }

    /**
     * 是否已在此轮攻击中攻击过对应实体
     */
    fun hasAttacked(entity: Entity) = entity.id in _attackedEntities // 直接访问内部set，避免触发不必要的同步



    // 新增字段
    var ticksSinceLastReset: Int = 0
        private set // 只允许内部修改

    var resetAfterTicks: Int? = null // null 或 <= 0 表示禁用自动重置
        set(value) {
            field = value
            // 如果设置为自动重置，并且当前 tick 计数已经超过或等于，则立即重置一次
            // 这主要是为了应对在运行中途改变此设置的情况
            if (value != null && value > 0 && ticksSinceLastReset >= value) {
                reset()
            }
            trySendSyncPacket() // 如果需要同步此设置，可以在这里添加逻辑，但目前 AttackSystemSyncPayload 不包含此字段
        }

    internal fun internalSetTicksSinceLastReset(value: Int) {
        if (this.ticksSinceLastReset != value) {
            this.ticksSinceLastReset = value
            // No trySendSyncPacket() here, this is for client update
        }
    }

    private fun trySendSyncPacket() {
        val currentEntity = associatedEntity
        val currentCbId = collisionBoxId
        if (currentEntity != null && currentCbId != null && !currentEntity.level().isClientSide) {
            // 检查是否为 EntityCustomNpc
            if (ModList.get().isLoaded("customnpcs")) {
                try {
                    val entityCustomNpcClass = Class.forName("noppes.npcs.entity.EntityCustomNpc")
                    if (entityCustomNpcClass.isInstance(currentEntity)) {
                        SparkCore.LOGGER.info("EntityCustomNpc加载成功")
                    }
                } catch (e: Exception) {
                    // 忽略反射错误，继续执行
                }
            }
            val payload = AttackSystemSyncPayload(
                currentEntity.syncerType,
                currentEntity.syncData,
                currentCbId,
                cbName,
                _attackedEntities.toHashSet(), // 发送副本以避免并发修改问题和确保不可变性
                ignoreInvulnerableTime,
                autoResetEnabled,
                ticksSinceLastReset, // Add new field
                resetAfterTicks // Add new field
            )
            PacketDistributor.sendToPlayersTrackingEntity(currentEntity, payload)
        }
    }

    fun customAttack(target: Entity, customLogic: () -> Boolean): Boolean {
        // 检查是否为 EntityCustomNpc 且为 rightArm，使用反射安全检查
        if (ModList.get().isLoaded("customnpcs") && this.collisionBoxId == "rightArm" && this.associatedEntity?.level() is ClientLevel) {
            try {
                val entityCustomNpcClass = Class.forName("noppes.npcs.entity.EntityCustomNpc")
                if (entityCustomNpcClass.isInstance(this.associatedEntity)) {
                    println("123456")
                }
            } catch (e: Exception) {
                // 忽略反射错误，继续执行
            }
        }
        if (autoResetEnabled) {
            ticksSinceLastReset++ // 每次调用时递增，当自动重置开启时
            
            // 如果当前tick数超过resetAfterTicks，则执行重置
            // 或者如果等于resetAfterTicks且resetAfterTicks为1（意味着在第一次成功攻击后每个tick都重置）
            // 更简单地说：如果ticksSinceLastReset大于resetAfterTicks，则重置
            // 或者如果ticksSinceLastReset等于resetAfterTicks，并且我们即将进行第(resetAfterTicks)次成功攻击
            // 这里采用ticksSinceLastReset > resetAfterTicks作为重置条件
            // reset()方法会将ticksSinceLastReset设置为0
            // 所以当resetAfterTicks为1时：
            // 第1次调用: ticks=1. 1>1 为false. 执行攻击.
            // 第2次调用: ticks=2. 2>1 为true. 执行重置 (ticks=0). 将ticks设为1表示新序列的第一个tick
            if (ticksSinceLastReset > resetAfterTicks!!) {
                reset() // 这会将ticksSinceLastReset设为0
                // 重置后，当前调用实际上是新序列的第一个tick
                ticksSinceLastReset = 1 // 将当前调用计为新序列的第一个tick
            }
        }

        if (hasAttacked(target)) {
            // 如果目标仍在列表中，说明自动重置未启用，或尚未达到重置tick数
            return false
        }

        if (customLogic()) {
            _attackedEntities.add(target.id)
            trySendSyncPacket() // 手动触发同步
            return true
        }
        
        // 如果customLogic返回false，攻击未发生
        // 如果启用了自动重置，ticksSinceLastReset已经为这次"尝试"递增了
        return false
    }

    /**
     * 重置攻击系统状态，清除已攻击实体列表，并重置tick计数器。
     */
    fun reset() {
        val changed = _attackedEntities.isNotEmpty()
        _attackedEntities.clear()
        if (ignoreInvulnerableTime) ticksSinceLastReset = 0 // 重置tick计数器
        if (changed) {
            trySendSyncPacket() // 手动触发同步
        }
    }
}