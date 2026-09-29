package cn.solarmoon.spark_core.physics.body

object CollisionGroups {
    /**
     * 空碰撞组 / Empty collision group
     *
     * 重要：此标志只能用于刚体的碰撞掩码(collideWith)，表示不与任何组碰撞。
     * 不能将刚体的碰撞组设置为NONE，否则该刚体将无法参与任何碰撞检测。
     *
     * Important: This flag can only be used in a rigid body's collision mask (collideWith),
     * indicating it collides with nothing. Cannot set a rigid body's collision group to NONE,
     * otherwise it won't participate in any collision detection.
     */
    const val NONE = 0

    /**
     * 地形碰撞组 / Terrain collision group
     *
     * 用于静态地形几何体，如地面、墙壁等。
     *
     * Used for static terrain geometry like ground, walls, etc.
     */
    const val TERRAIN = 1 shl 0

    /**
     * 物理实体碰撞组 / Physics body collision group
     *
     * 用于动态物理实体，如箱子、球体等可移动物体。
     *
     * Used for dynamic physics bodies like crates, spheres, and other movable objects.
     */
    const val PHYSICS_BODY = 1 shl 1

    /**
     * 角色碰撞组 / Pawn collision group
     *
     * 用于游戏中的角色实体，如玩家、NPC等。
     *
     * Used for character entities in the game like players, NPCs, etc.
     */
    const val PAWN = 1 shl 2

    /**
     * 触发器碰撞组 / Trigger collision group
     *
     * 用于触发器体积，用于检测进入/离开事件但不产生物理碰撞响应。
     *
     * Used for trigger volumes that detect enter/exit events but don't produce physical collision responses.
     */
    const val TRIGGER = 1 shl 3

    /**
     * 投射物碰撞组 / Projectile collision group
     *
     * 用于投射物的运动学触发体：一个只被射线与凸体扫掠查询看到的可探测体积。
     * 触发体的 collideWith 为 [NONE]，因此不与任何组产生接触对、不产生接触响应。
     *
     * Used for projectile trigger volumes: detectable volumes visible only to ray and
     * convex sweep queries. Their collideWith is [NONE], so they form no contact pairs.
     */
    const val PROJECTILE = 1 shl 4

    /**
     * 参与 WorldSnapshot 追踪的碰撞组。
     *
     * 决定主线程只读查询（rayTest / sweepTest / collectBodiesInAabb）可见的刚体范围。
     * 新增组的可见性只需改这一行。
     *
     * Collision groups tracked by WorldSnapshot, i.e. the set of bodies visible to
     * main-thread read-only queries.
     */
    val SNAPSHOT_TRACKED = setOf(PHYSICS_BODY, PROJECTILE)
}
