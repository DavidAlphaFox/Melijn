package me.melijn.melijnbot.commands.animal

import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.CommandContext
import me.melijn.melijnbot.internals.command.RunCondition
import me.melijn.melijnbot.internals.embed.Embedder
import me.melijn.melijnbot.internals.utils.message.sendEmbedRsp
import kotlin.random.Random


class NyancatCommand : AbstractCommand("command.nyancat") {

    init {
        id = 49
        name = "nyancat"
        aliases = arrayOf("nyan", "nya")
        runConditions = arrayOf(RunCondition.VOTED)
        commandCategory = CommandCategory.ANIMAL
    }

    override suspend fun execute(context: CommandContext) {
        val title = context.getTranslation("$root.title")

        val eb = Embedder(context)
            .setTitle(title)
            .setImage(getRandomNyancatUrl())
        sendEmbedRsp(context, eb.build())
    }

    private fun getRandomNyancatUrl(): String {
        val randomInt = Random.nextInt(2, 33)
        return "https://github.com/ToxicMushroom/nyan-cats/raw/master/cat%20($randomInt).gif"
    }
}