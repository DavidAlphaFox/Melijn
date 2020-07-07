package me.melijn.melijnbot.commands.anime

import me.melijn.melijnbot.objects.command.AbstractCommand
import me.melijn.melijnbot.objects.command.CommandCategory
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.embed.Embedder
import me.melijn.melijnbot.objects.utils.message.sendEmbedRsp
import kotlin.random.Random

class AIWaifuCommand : AbstractCommand("command.aiwaifu") {

    init {
        id = 145
        name = "aiWaifu"
        commandCategory = CommandCategory.ANIME
    }

    override suspend fun execute(context: CommandContext) {
        val title = context.getTranslation("$root.title")

        val eb = Embedder(context)
            .setTitle(title, "https://www.gwern.net/TWDNE")
            .setImage(getRandomAiWaifuUrl())
        sendEmbedRsp(context, eb.build())
    }

    private fun getRandomAiWaifuUrl(): String {
        return "https://www.thiswaifudoesnotexist.net/v2/example-${Random.nextInt(0, 190_000)}.jpg"
    }
}