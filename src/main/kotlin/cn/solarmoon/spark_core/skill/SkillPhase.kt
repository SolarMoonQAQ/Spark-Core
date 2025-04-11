package cn.solarmoon.spark_core.skill

enum class SkillPhase {
    /**
     * 技能已创建但未激活
     */
    IDLE,

    /**
     * 前摇阶段（技能启动但未产生效果）
     */
    WINDUP,

    /**
     * 生效阶段（技能产生实际效果）
     */
    ACTIVE,

    /**
     * 后摇阶段（效果结束但未完全终止）
     */
    RECOVERY,

    /**
     * 技能完全结束
     */
    END;

}