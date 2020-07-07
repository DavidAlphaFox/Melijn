package me.melijn.melijnbot.commands.administration

import kotlinx.coroutines.future.await
import me.melijn.melijnbot.enums.VerificationType
import me.melijn.melijnbot.objects.command.AbstractCommand
import me.melijn.melijnbot.objects.command.CommandCategory
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.translation.PLACEHOLDER_ARG
import me.melijn.melijnbot.objects.utils.enumValueOrNull
import me.melijn.melijnbot.objects.utils.message.sendRsp
import me.melijn.melijnbot.objects.utils.toUCC
import me.melijn.melijnbot.objects.utils.withVariable

class SetVerificationTypeCommand : AbstractCommand("command.setverificationtype") {

    init {
        id = 43
        name = "setVerificationType"
        aliases = arrayOf("svt")
        commandCategory = CommandCategory.ADMINISTRATION
    }

    override suspend fun execute(context: CommandContext) {
        val wrapper = context.daoManager.verificationTypeWrapper
        if (context.args.isEmpty()) {
            val type = wrapper.verificationTypeCache.get(context.guildId).await()
            val part = if (type == VerificationType.NONE) "unset" else "set"
            val msg = context.getTranslation("$root.show.$part")
                .withVariable("type", type.toUCC())
            sendRsp(context, msg)
            return
        }


        val type = enumValueOrNull<VerificationType>(context.rawArg)
        val msg = if (context.rawArg == "null" || type == VerificationType.NONE) {
            wrapper.removeType(context.guildId)
            context.getTranslation("$root.unset")
        } else if (type == null) {
            context.getTranslation("message.unknown.verificationtype")
                .withVariable(PLACEHOLDER_ARG, context.rawArg)
        } else {
            wrapper.setType(context.guildId, type)
            context.getTranslation("$root.set")
                .withVariable(PLACEHOLDER_ARG, type.toUCC())
        }

        sendRsp(context, msg)
    }
}