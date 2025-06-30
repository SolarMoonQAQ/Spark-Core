package cn.solarmoon.spark_core.command

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

// 继承 BaseCommand，设置命令头为 "getwand"，权限等级为 2
class GetWandCommand : BaseCommand("getwand", 2) {

    override fun putExecution(context: CommandBuildContext) {
        // 直接在 builder 上设置执行逻辑
        builder.executes(this::executeGetWand)
    }

    private fun executeGetWand(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.playerOrException // 获取执行命令的玩家

        // 从 DeferredHolder 获取实际的 Item 实例来创建 ItemStack
        val wandStack = ItemStack(SparkRegistries.MODEL_EDITOR_WAND.get()) // Use .get()

        // 尝试将物品给予玩家的物品栏
        val added = player.inventory.add(wandStack)
        if (!added) {
            // 如果物品栏满了，尝试在玩家脚下生成掉落物实体
            player.drop(wandStack, false)
            source.sendSuccess({ Component.literal("物品栏已满，魔杖已掉落在地上!") }, false) // 发送反馈，不广播给其他管理员
        } else {
            source.sendSuccess({ Component.literal("给予模型编辑器魔杖!") }, false) // 发送反馈，不广播给其他管理员
        }

        return 1 // 返回 1 表示命令成功执行
    }
}