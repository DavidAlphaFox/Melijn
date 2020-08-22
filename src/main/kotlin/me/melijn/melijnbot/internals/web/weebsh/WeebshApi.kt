package me.melijn.melijnbot.internals.web.weebsh

import me.duncte123.weebJava.WeebApiBuilder
import me.duncte123.weebJava.models.WeebApi
import me.duncte123.weebJava.types.NSFWMode
import me.duncte123.weebJava.types.TokenType
import me.melijn.melijnbot.internals.Settings
import me.melijn.melijnbot.internals.translation.MISSING_IMAGE_URL
import me.melijn.melijnbot.internals.utils.toLCC
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WeebshApi(val settings: Settings) {

    private val weebApi: WeebApi = WeebApiBuilder(TokenType.WOLKETOKENS)
        .setBotInfo("Melijn", "latest", settings.environment.toLCC())
        .setToken(settings.tokens.weebSh)
        .build()

    suspend fun getUrl(type: String, nsfw: Boolean = false): String = suspendCoroutine {
        val nsfwEnum = if (nsfw) NSFWMode.ONLY_NSFW else NSFWMode.DISALLOW_NSFW
        weebApi.getRandomImage(type, nsfwEnum).async({ image ->
            it.resume(image.url)
        }, { _ ->
            it.resume(MISSING_IMAGE_URL)
        })
    }
}