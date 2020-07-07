package me.melijn.melijnbot.commands.utility

import me.melijn.melijnbot.objects.command.AbstractCommand
import me.melijn.melijnbot.objects.command.CommandCategory
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.utils.message.sendRsp
import me.melijn.melijnbot.objects.utils.message.sendSyntax

class RawCommand : AbstractCommand("command.raw") {

    init {
        id = 118
        name = "raw"
        commandCategory = CommandCategory.UTILITY
    }

    override suspend fun execute(context: CommandContext) {
        if (context.args.isEmpty()) {
            sendSyntax(context)
            return
        }
        sendRsp(context, "```${context.rawArg}```")
    }
}