package me.melijn.melijnbot.commands.administration

import me.melijn.melijnbot.objects.command.AbstractCommand
import me.melijn.melijnbot.objects.command.CommandCategory
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.utils.*
import me.melijn.melijnbot.objects.utils.message.sendMsgAwaitEL
import me.melijn.melijnbot.objects.utils.message.sendRsp
import me.melijn.melijnbot.objects.utils.message.sendSyntax
import net.dv8tion.jda.api.Permission

class SetRoleColorCommand : AbstractCommand("command.setrolecolor") {

    init {
        id = 167
        name = "setRoleColor"
        aliases = arrayOf("src")
        discordChannelPermissions = arrayOf(Permission.MESSAGE_ATTACH_FILES)
        commandCategory = CommandCategory.ADMINISTRATION
    }

    override suspend fun execute(context: CommandContext) {
        if (context.args.isEmpty()) {
            sendSyntax(context)
            return
        }

        if (context.args.size == 1) {
            val role = getRoleByArgsNMessage(context, 0) ?: return
            val color = role.color
            if (color == null) {

                val msg = context.getTranslation("$root.show.unset")
                    .withVariable("role", role.name)
                sendRsp(context, msg)
            } else {
                val plane = ImageUtils.createPlane(100, color.rgb)

                val msg = context.getTranslation("$root.show")
                    .withVariable("role", role.name)
                    .withVariable("color", color.toHex())
                sendMsgAwaitEL(context, msg, plane, "jpg")
            }

            return
        }

        val role = getRoleByArgsNMessage(context, 0, canInteract = true) ?: return
        val oldColor = role.color

        if (context.args[1] == "null") {
            role.manager.setColor(null).reason("setRoleColor command").queue()

            val msg = context.getTranslation("$root.unset")
                .withVariable("role", role.name)
                .withVariable("oldColor", oldColor?.toHex() ?: "/")
            sendRsp(context, msg)
        } else {
            val color = getColorFromArgNMessage(context, 1) ?: return
            role.manager.setColor(color).reason("setRoleColor command").queue()
            val plane = ImageUtils.createPlane(100, color.rgb)

            val msg = context.getTranslation("$root.set")
                .withVariable("role", role.name)
                .withVariable("oldColor", oldColor?.toHex() ?: "/")
                .withVariable("color", color.toHex())
            sendMsgAwaitEL(context, msg, plane, "jpg")
        }
    }
}