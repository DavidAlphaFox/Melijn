package me.melijn.melijnbot.objects.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import me.melijn.melijnbot.Container
import me.melijn.melijnbot.database.command.CustomCommand
import me.melijn.melijnbot.enums.ChannelCommandState
import me.melijn.melijnbot.objects.translation.getLanguage
import me.melijn.melijnbot.objects.translation.i18n
import me.melijn.melijnbot.objects.utils.sendInGuild
import me.melijn.melijnbot.objects.utils.sendMsg
import me.melijn.melijnbot.objects.utils.toUpperWordCase
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.regex.Pattern

class CommandClient(private val commandList: Set<AbstractCommand>, private val container: Container) : ListenerAdapter() {

    private val guildPrefixCache = container.daoManager.guildPrefixWrapper.prefixCache
    private val userPrefixCache = container.daoManager.userPrefixWrapper.prefixCache

    private val commandCooldownCache = container.daoManager.commandCooldownWrapper.commandCooldownCache
    private val channelCommandCooldownCache = container.daoManager.commandChannelCoolDownWrapper.commandChannelCooldownCache

    private val disabledCommandCache = container.daoManager.disabledCommandWrapper.disabledCommandsCache
    private val channelCommandStateCache = container.daoManager.channelCommandStateWrapper.channelCommandsStateCache


    private val commandMap: HashMap<String, AbstractCommand> = HashMap()

    init {
        commandList.forEach { command ->
            commandMap[command.name.toLowerCase()] = command
            for (alias in command.aliases) {
                commandMap[alias.toLowerCase()] = command
            }
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                commandRunner(event)
            } catch (e: Exception) {
                e.printStackTrace()

                if (event.isFromType(ChannelType.PRIVATE)) {
                    e.sendInGuild(channel = event.privateChannel, author = event.author)
                } else if (event.isFromType(ChannelType.TEXT)) {
                    e.sendInGuild(event.guild, event.textChannel, event.author)
                }
            }
        }
    }

    private suspend fun commandRunner(event: MessageReceivedEvent) {
        val prefixes = getPrefixes(event)
        val message = event.message

        val ccsWithPrefix = mutableListOf<CustomCommand>()
        val ccsWithoutPrefix = mutableListOf<CustomCommand>()
        if (event.isFromGuild) {

            val ccWrapper = container.daoManager.customCommandWrapper
            val ccs = ccWrapper.customCommandCache.get(event.guild.idLong).await()

            for (cc in ccs) {
                if (cc.prefix) {
                    ccsWithPrefix
                } else {
                    ccsWithoutPrefix
                }.add(cc)
            }
        }


        for (prefix in prefixes) {
            if (!message.contentRaw.startsWith(prefix)) continue

            val commandParts: ArrayList<String> = ArrayList(message.contentRaw
                .replaceFirst(Regex("${Pattern.quote(prefix)}(\\s+)?"), "")
                .split(Regex("\\s+")))
            commandParts.add(0, prefix)

            val command = commandMap.getOrElse(commandParts[1].toLowerCase(), { null }) ?: continue
            if (checksFailed(command, event)) return
            command.run(CommandContext(event, commandParts, container, commandList))
            return
        }


    }

    private suspend fun getPrefixes(event: MessageReceivedEvent): List<String> {
        var prefixes =
            if (event.isFromGuild)
                guildPrefixCache.get(event.guild.idLong).await().toMutableList()
            else mutableListOf()

        //add default prefix if none are set
        if (prefixes.isEmpty()) prefixes = mutableListOf(container.settings.prefix)

        //registering private prefixes
        if (container.daoManager.supporterWrapper.supporterIds.contains(event.author.idLong))
            prefixes.addAll(userPrefixCache.get(event.author.idLong).await())

        //mentioning the bot will always work
        prefixes.add(
            if (event.isFromGuild) event.guild.selfMember.asMention
            else event.jda.selfUser.asMention
        )
        return prefixes.toList()
    }

    /**
     * [@return] returns true if the check failed
     *
     * **/
    private suspend fun checksFailed(command: AbstractCommand, event: MessageReceivedEvent): Boolean {
        val cmdId = command.id.toString()
        if (event.isFromGuild && commandIsDisabled(cmdId, event)) {
            return true
        }

        command.runConditions.forEach {
            if (!runConditionCheckPassed(it, event)) return true
        }

        if (event.isFromGuild) {
            command.discordPermissions.forEach { permission ->
                val botMember = event.guild.selfMember
                var missingPermissionCount = 0
                var missingPermissionMessage = ""

                if (!botMember.hasPermission(event.textChannel, permission)) {
                    missingPermissionMessage += "\n ⁎**${permission.toString().toUpperWordCase()}**"
                    missingPermissionCount++
                }

                if (missingPermissionCount > 0) {
                    missingPermissionMessage =
                        "I'm missing the following permission" +
                            (if (missingPermissionCount > 1) "s" else "") +
                            missingPermissionMessage

                    sendMsg(event.textChannel, missingPermissionMessage)
                    return true
                }
            }

            if (commandIsOnCooldown(cmdId, event)) {
                return true
            }
        }

        return false
    }

    private suspend fun commandIsDisabled(id: String, event: MessageReceivedEvent): Boolean {
        val disabledChannelCommands = channelCommandStateCache.get(event.channel.idLong).await()
        if (disabledChannelCommands.contains(id)) {
            when (disabledChannelCommands[id]) {
                ChannelCommandState.ENABLED -> return false
                ChannelCommandState.DISABLED -> return true
            }
        }

        val disabledCommands = disabledCommandCache.get(event.guild.idLong).await()
        if (disabledCommands.contains(id)) return true

        return false
    }

    private suspend fun commandIsOnCooldown(id: String, event: MessageReceivedEvent): Boolean {
        val guildId = event.guild.idLong
        val userId = event.author.idLong
        val channelId = event.channel.idLong

        if (!container.daoManager.commandChannelCoolDownWrapper.executions.contains(Pair(guildId, userId))) return false

        var lastExecution = 0L
        var lastExecutionChannel = 0L
        var bool = false
        var cooldownResult = 0L

        val commandChannelCooldowns = channelCommandCooldownCache.get(channelId).await()
        if (commandChannelCooldowns.containsKey(id)) {

            //init lastExecutionChannel
            container.daoManager.commandChannelCoolDownWrapper.executions[Pair(channelId, userId)]
                ?.filter { entry -> entry.key == id }
                ?.forEach { entry ->
                    if (entry.value > lastExecutionChannel) lastExecutionChannel = entry.value
                }

            val cooldown = commandChannelCooldowns[id] ?: 0L

            if (System.currentTimeMillis() - cooldown < lastExecutionChannel) {
                cooldownResult = cooldown
                bool = true
            }
        }
        val commandCooldowns = commandCooldownCache.get(guildId).await()
        if (commandCooldowns.containsKey(id)) {

            //init lastExecution
            container.daoManager.commandChannelCoolDownWrapper.executions[Pair(guildId, userId)]
                ?.filter { entry -> entry.key == id }
                ?.forEach { entry ->
                    if (entry.value > lastExecution) lastExecution = entry.value
                }

            val cooldown = commandCooldowns[id] ?: 0L

            if (System.currentTimeMillis() - cooldown < lastExecutionChannel) {
                if (cooldownResult < cooldown) cooldownResult = cooldown
                bool = true
            }
        }
        val lastExecutionBiggest = if (lastExecution > lastExecutionChannel) lastExecution else lastExecutionChannel
        if (bool && cooldownResult != 0L) {

            val language = getLanguage(container.daoManager, userId, guildId)
            val unReplacedCooldown = i18n.getTranslation(language, "message.cooldown")
            val msg = unReplacedCooldown
                .replace("%cooldown%", ((cooldownResult - (System.currentTimeMillis() - lastExecutionBiggest)) / 1000.0).toString())
            sendMsg(event.textChannel, msg)
        }
        return bool
    }

    /**
     * [@return] returns true if the check passed
     *
     * **/
    private fun runConditionCheckPassed(runCondition: RunCondition, event: MessageReceivedEvent): Boolean {
        return when (runCondition) {
            RunCondition.GUILD -> event.isFromGuild
            RunCondition.VC_BOT_ALONE_OR_USER_DJ -> false
        }
    }


}