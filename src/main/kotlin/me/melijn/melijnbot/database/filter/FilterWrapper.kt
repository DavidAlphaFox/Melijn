package me.melijn.melijnbot.database.filter

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.future.await
import me.melijn.melijnbot.database.IMPORTANT_CACHE
import me.melijn.melijnbot.enums.FilterType
import me.melijn.melijnbot.objects.threading.TaskManager
import me.melijn.melijnbot.objects.utils.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class FilterWrapper(val taskManager: TaskManager, private val filterDao: FilterDao) {

    val allowedFilterCache = Caffeine.newBuilder()
        .expireAfterAccess(IMPORTANT_CACHE, TimeUnit.MINUTES)
        .executor(taskManager.executorService)
        .buildAsync<Pair<Long, Long?>, List<String>>() { (first, second), executor ->
            getFilters(first, second, FilterType.ALLOWED, executor)
        }

    val deniedFilterCache = Caffeine.newBuilder()
        .expireAfterAccess(IMPORTANT_CACHE, TimeUnit.MINUTES)
        .executor(taskManager.executorService)
        .buildAsync<Pair<Long, Long?>, List<String>>() { (first, second), executor ->
            getFilters(first, second, FilterType.DENIED, executor)
        }

    private fun getFilters(guildId: Long, channelId: Long?, filterType: FilterType, executor: Executor = taskManager.executorService): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()
        executor.launch {
            val filters = filterDao.get(guildId, channelId, filterType)
            future.complete(filters)
        }
        return future
    }

    suspend fun addFilter(guildId: Long, channelId: Long?, filterType: FilterType, filter: String) {
        filterDao.add(guildId, channelId, filterType, filter)

        val pair = Pair(guildId, channelId)
        when (filterType) {
            FilterType.ALLOWED -> {
                val newFilters = allowedFilterCache.get(pair).await().toMutableList() + filter
                allowedFilterCache.put(pair, CompletableFuture.completedFuture(newFilters))
            }
            FilterType.DENIED -> {
                val newFilters = deniedFilterCache.get(pair).await().toMutableList() + filter
                deniedFilterCache.put(pair, CompletableFuture.completedFuture(newFilters))
            }
        }
    }

    suspend fun removeFilter(guildId: Long, channelId: Long?, type: FilterType, filter: String) {
        filterDao.remove(guildId, channelId, type, filter)

        val pair = Pair(guildId, channelId)
        when (type) {
            FilterType.ALLOWED -> {
                val newFilters = allowedFilterCache.get(pair).await().toMutableList() - filter
                allowedFilterCache.put(pair, CompletableFuture.completedFuture(newFilters))
            }
            FilterType.DENIED -> {
                val newFilters = deniedFilterCache.get(pair).await().toMutableList() - filter
                deniedFilterCache.put(pair, CompletableFuture.completedFuture(newFilters))
            }
        }
    }

    suspend fun contains(guildId: Long, channelId: Long?, type: FilterType, filter: String): Boolean {
        val pair = Pair(guildId, channelId)
        return when (type) {
            FilterType.ALLOWED -> allowedFilterCache
            FilterType.DENIED -> deniedFilterCache
        }.get(pair).await().contains(filter)
    }
}