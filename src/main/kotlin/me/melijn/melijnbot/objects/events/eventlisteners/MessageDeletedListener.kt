package me.melijn.melijnbot.objects.events.eventlisteners

import kotlinx.coroutines.future.await
import me.melijn.melijnbot.Container
import me.melijn.melijnbot.database.message.DaoMessage
import me.melijn.melijnbot.enums.LogChannelType
import me.melijn.melijnbot.objects.events.AbstractListener
import me.melijn.melijnbot.objects.translation.PLACEHOLDER_CHANNEL
import me.melijn.melijnbot.objects.translation.PLACEHOLDER_USER
import me.melijn.melijnbot.objects.translation.getLanguage
import me.melijn.melijnbot.objects.translation.i18n
import me.melijn.melijnbot.objects.utils.*
import me.melijn.melijnbot.objects.utils.checks.getAndVerifyLogChannelById
import me.melijn.melijnbot.objects.utils.message.escapeForLog
import me.melijn.melijnbot.objects.utils.message.sendEmbed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogOption
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import java.awt.Color
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors


class MessageDeletedListener(container: Container) : AbstractListener(container) {

    override fun onEvent(event: GenericEvent) {
        if (event is GuildMessageDeleteEvent) {
            container.taskManager.async {
                onGuildMessageDelete(event)
                removePurgeIdMaybe(event)
            }
        }
    }

    private fun removePurgeIdMaybe(event: GuildMessageDeleteEvent) {
        container.purgedIds.remove(event.messageIdLong)
    }

    private suspend fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        val guild = event.guild
        val guildId = event.guild.idLong
        val daoManager = container.daoManager
        val logChannelCache = daoManager.logChannelWrapper.logChannelCache
        if (!guild.selfMember.hasPermission(Permission.VIEW_AUDIT_LOGS)) return

        val odmId = logChannelCache.get(Pair(guildId, LogChannelType.OTHER_DELETED_MESSAGE)).await()
        val sdmId = logChannelCache.get(Pair(guildId, LogChannelType.SELF_DELETED_MESSAGE)).await()
        val fmId = logChannelCache.get(Pair(guildId, LogChannelType.FILTERED_MESSAGE)).await()
        if (odmId == -1L && sdmId == -1L && fmId == -1L) return

        val odmLogChannel = guild.getAndVerifyLogChannelById(daoManager, LogChannelType.OTHER_DELETED_MESSAGE, odmId)
        val sdmLogChannel = guild.getAndVerifyLogChannelById(daoManager, LogChannelType.SELF_DELETED_MESSAGE, sdmId)
        val fmLogChannel = guild.getAndVerifyLogChannelById(daoManager, LogChannelType.FILTERED_MESSAGE, fmId)
        if (odmLogChannel == null && sdmLogChannel == null && fmLogChannel == null) return

        selectCorrectLogType(event, odmLogChannel, sdmLogChannel, fmLogChannel)
    }

    private suspend fun selectCorrectLogType(
        event: GuildMessageDeleteEvent,
        odmLogChannel: TextChannel?,
        sdmLogChannel: TextChannel?,
        fmLogChannel: TextChannel?
    ) {
        val guild = event.guild
        val msg = container.daoManager.messageHistoryWrapper.getMessageById(event.messageIdLong) ?: return
        val msgDeleteTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.of("GMT"))

        when {
            container.purgedIds.keys.contains(msg.messageId) -> {
                container.purgedIds.remove(msg.messageId)
                return
            }
            container.filteredMap.keys.contains(msg.messageId) -> {
                postDeletedByFilterLog(fmLogChannel, msg, event, container.filteredMap[msg.messageId])
                container.filteredMap.remove(msg.messageId)
                return
            }
            container.botDeletedMessageIds.contains(msg.messageId) -> {
                postDeletedByOtherLog(odmLogChannel, msg, event, event.guild.selfMember)
                container.botDeletedMessageIds.remove(msg.messageId)
                return
            }
            else -> {
                val list = guild.retrieveAuditLogs().type(ActionType.MESSAGE_DELETE).limit(50).await()
                val filtered = list.stream()
                    .filter {
                        it.getOption<String>(AuditLogOption.CHANNEL)?.toLong() == msg.textChannelId &&
                            it.targetIdLong == msg.authorId &&
                            it.timeCreated.until(msgDeleteTime, ChronoUnit.MINUTES) <= 5
                    }
                    .collect(Collectors.toList())

                val entry = when {
                    filtered.size > 1 -> {
                        filtered.sortBy { logEntry ->
                            logEntry.timeCreated
                        }

                        filtered.asReversed()[0]
                    }
                    filtered.size == 1 -> filtered[0]
                    else -> null
                }

                if (entry != null) {
                    val user = entry.user ?: return
                    val member = event.guild.retrieveMember(user).awaitOrNull() ?: return

                    postDeletedByOtherLog(odmLogChannel, msg, event, member)
                } else {
                    postDeletedBySelfLog(sdmLogChannel, msg, event)
                }
            }
        }
    }

    private suspend fun postDeletedBySelfLog(sdmLogChannel: TextChannel?, msg: DaoMessage, event: GuildMessageDeleteEvent) {
        if (sdmLogChannel == null) return
        val messageAuthor = event.jda.shardManager?.retrieveUserById(msg.authorId)?.awaitOrNull() ?: return
        val botLogState = container.daoManager.botLogStateWrapper.botLogStateCache[sdmLogChannel.guild.idLong].await()
        if (!botLogState && messageAuthor.isBot) return

        val ebs = getGeneralEmbedBuilder(msg, event, messageAuthor, messageAuthor.idLong)
        for ((index, eb) in ebs.withIndex()) {
            eb.setColor(Color.decode("#000001"))

            if (index == ebs.size - 1) {
                val language = getLanguage(container.daoManager, -1, event.guild.idLong)
                val footer = i18n.getTranslation(language, "listener.message.deletion.log.footer")
                    .withVariable(PLACEHOLDER_USER, messageAuthor.asTag)
                eb.setFooter(footer, messageAuthor.effectiveAvatarUrl)
            }
            sendEmbed(container.daoManager.embedDisabledWrapper, sdmLogChannel, eb.build())
        }
    }

    private suspend fun postDeletedByOtherLog(odmLogChannel: TextChannel?, msg: DaoMessage, event: GuildMessageDeleteEvent, deleterMember: Member) {
        if (odmLogChannel == null) return
        val messageAuthor = event.jda.shardManager?.retrieveUserById(msg.authorId)?.awaitOrNull() ?: return
        val botLogState = container.daoManager.botLogStateWrapper.botLogStateCache[odmLogChannel.guild.idLong].await()
        if (!botLogState && messageAuthor.isBot) return

        val ebs = getGeneralEmbedBuilder(msg, event, messageAuthor, deleterMember.idLong)
        for ((index, eb) in ebs.withIndex()) {
            eb.setColor(Color.decode("#000001"))
            if (index == ebs.size - 1) {
                val language = getLanguage(container.daoManager, -1, event.guild.idLong)
                val footer = i18n.getTranslation(language, "listener.message.deletion.log.footer")
                    .withVariable(PLACEHOLDER_USER, deleterMember.asTag)
                eb.setFooter(footer, deleterMember.user.effectiveAvatarUrl)
            }

            sendEmbed(container.daoManager.embedDisabledWrapper, odmLogChannel, eb.build())
        }
    }

    private suspend fun postDeletedByFilterLog(fmLogChannel: TextChannel?, msg: DaoMessage, event: GuildMessageDeleteEvent, causeArgs: Map<String, List<String>>?) {
        if (fmLogChannel == null) return
        val messageAuthor = event.jda.shardManager?.retrieveUserById(msg.authorId)?.awaitOrNull() ?: return
        val botLogState = container.daoManager.botLogStateWrapper.botLogStateCache[fmLogChannel.guild.idLong].await()
        if (!botLogState && messageAuthor.isBot) return

        val ebs = getGeneralEmbedBuilder(msg, event, messageAuthor, event.jda.selfUser.idLong)
        for ((index, eb) in ebs.withIndex()) {
            eb.setColor(Color.YELLOW)

            if (index == ebs.size - 1) {
                val language = getLanguage(container.daoManager, -1, event.guild.idLong)
                val fieldTitle = i18n.getTranslation(language, "detected") + ":"
                var extra = ""
                causeArgs?.let {
                    for ((key, value) in it) {
                        if (value.isEmpty()) continue
                        extra += i18n.getTranslation(language, "logging.punishmentpoints.cause.${key}")
                            .withVariable("word", value.joinToString()) + "\n"
                    }
                }

                eb.addField(fieldTitle, extra.take(MessageEmbed.VALUE_MAX_LENGTH), false)

                val footer = i18n.getTranslation(language, "listener.message.deletion.log.footer")
                    .withVariable(PLACEHOLDER_USER, event.jda.selfUser.asTag)
                eb.setFooter(footer, event.jda.selfUser.effectiveAvatarUrl)
            }
            sendEmbed(container.daoManager.embedDisabledWrapper, fmLogChannel, eb.build())
        }
    }

    private suspend fun getGeneralEmbedBuilder(
        msg: DaoMessage,
        event: GuildMessageDeleteEvent,
        messageAuthor: User,
        messageDeleterId: Long
    ): List<EmbedBuilder> {

        val daoManager = container.daoManager
        val zoneId = getZoneId(daoManager, event.guild.idLong)
        val channel = event.guild.getTextChannelById(msg.textChannelId)


        val language = getLanguage(container.daoManager, -1, event.guild.idLong)
        val title = i18n.getTranslation(language, "listener.message.deletion.log.title")
            .withVariable(PLACEHOLDER_CHANNEL, channel?.asTag ?: "<#${msg.textChannelId}>")

        val extra = if (msg.authorId == messageDeleterId) ".self" else ""
        val description = i18n.getTranslation(language, "listener.message.deletion.log${extra}.description")
            .withVariable("messageAuthor", messageAuthor.asTag)
            .withVariable("messageContent", escapeForLog(msg.content))
            .withVariable("messageAuthorId", msg.authorId.toString())
            .withVariable("messageDeleterId", messageDeleterId.toString())
            .withVariable("sentTime", msg.moment.asEpochMillisToDateTime(zoneId))
            .withVariable("deletedTime", System.currentTimeMillis().asEpochMillisToDateTime(zoneId))

        val ebs = mutableListOf<EmbedBuilder>()

        val embedBuilder = EmbedBuilder()
        embedBuilder.setTitle(title)
        embedBuilder.setThumbnail(messageAuthor.effectiveAvatarUrl)
        if (description.length > MessageEmbed.TEXT_MAX_LENGTH) {
            val parts = StringUtils.splitMessageWithCodeBlocks(description, lang = "LDIF")
            embedBuilder.setDescription(parts[0])
            ebs.add(embedBuilder)
            for (part in parts.subList(1, parts.size)) {
                val embedBuilder2 = EmbedBuilder()
                embedBuilder2.setDescription(part)
                ebs.add(embedBuilder2)
            }
        } else {
            embedBuilder.setDescription(description)
            ebs.add(embedBuilder)
        }
        return ebs
    }
}