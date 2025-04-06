package cn.solarmoon.spark_core.command

abstract class SkillCommand(permissionLevel: Int): BaseCommand("skill", permissionLevel) {
}