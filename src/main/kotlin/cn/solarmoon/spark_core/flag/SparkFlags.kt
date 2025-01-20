package cn.solarmoon.spark_core.flag

object SparkFlags {

    /**
     * 对玩家：阻止预输入
     */
    val DISABLE_PRE_INPUT = Flag("disable_preinput")

    /**
     * 对玩家：阻止攻击输入
     * 对生物：阻止生物的一般攻击
     */
    val DISARM = Flag("disarm")

    /**
     * 对玩家：阻止使用物品和方块交互
     * 对生物：阻止使用物品
     */
    val SILENCE = Flag("silence")

    /**
     * 对玩家：冻结移动输入
     * 对生物：缓慢max
     */
    val MOVE_INPUT_FREEZE = Flag("move_input_freeze")

}