package cn.solarmoon.spark_core.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * 基本的命令模版
 * @param head 头部命令行名称
 * @param permissionLevel 权限等级
 */
abstract class BaseCommand(
    val head: String,
    val permissionLevel: Int
) {

    protected var builder = Commands.literal(head).requires { it.hasPermission(permissionLevel) }
        private set

    /**
     * 用于放入后续指令
     */
    protected abstract fun putExecution(context: CommandBuildContext)

    fun create(context: CommandBuildContext): LiteralArgumentBuilder<CommandSourceStack> {
        putExecution(context)
        return builder
    }

    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(create(event.buildContext))
    }

}