package cn.solarmoon.spark_core.skill.node.bases

enum class ParallelPolicy {
    /**
     * 所有子节点成功才算成功
     */
    ALL_SUCCESS,

    /**
     * 任意子节点成功即成功
     */
    ANY_SUCCESS,

    /**
     * 所有子节点失败才算失败
     */
    ALL_FAILURE,

    /**
     * 任意子节点失败即失败
     */
    ANY_FAILURE
}