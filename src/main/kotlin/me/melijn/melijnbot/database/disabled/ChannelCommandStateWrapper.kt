package me.melijn.melijnbot.database.disabled

import com.fasterxml.jackson.module.kotlin.readValue
import me.melijn.melijnbot.database.HIGHER_CACHE
import me.melijn.melijnbot.database.NORMAL_CACHE
import me.melijn.melijnbot.internals.models.TriState
import me.melijn.melijnbot.objectMapper

class ChannelCommandStateWrapper(private val channelCommandStateDao: ChannelCommandStateDao) {

    suspend fun getMap(channelId: Long): Map<String, TriState> {
        val cached = channelCommandStateDao.getCacheEntry(channelId, HIGHER_CACHE)?.let {
            objectMapper.readValue<Map<String, TriState>>(it)
        }
        if (cached != null) return cached

        val map = channelCommandStateDao.get(channelId)
        channelCommandStateDao.setCacheEntry(channelId, objectMapper.writeValueAsString(map), NORMAL_CACHE)
        return map
    }

    suspend fun setCommandState(
        guildId: Long,
        channelId: Long,
        commandIds: Set<String>,
        channelCommandState: TriState
    ) {
        val map = getMap(channelId).toMutableMap()
        if (channelCommandState == TriState.DEFAULT) {
            channelCommandStateDao.bulkRemove(channelId, commandIds)
            for (id in commandIds) {
                map.remove(id)
            }
        } else {
            channelCommandStateDao.bulkPut(guildId, channelId, commandIds, channelCommandState)
            for (id in commandIds) {
                map[id] = channelCommandState
            }
        }
        channelCommandStateDao.setCacheEntry(channelId, objectMapper.writeValueAsString(map), NORMAL_CACHE)
    }

    fun migrateChannel(oldId: Long, newId: Long) {
        channelCommandStateDao.migrateChannel(oldId, newId)
    }

    fun invalidate(oldId: Long) {
        channelCommandStateDao.removeCacheEntry(oldId)
    }
}