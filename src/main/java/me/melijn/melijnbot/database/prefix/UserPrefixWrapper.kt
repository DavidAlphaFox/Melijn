package me.melijn.melijnbot.database.prefix

import com.github.benmanes.caffeine.cache.Caffeine
import me.melijn.melijnbot.database.FREQUENTLY_USED_CACHE
import me.melijn.melijnbot.objects.threading.TaskManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class UserPrefixWrapper(private val taskManager: TaskManager, private val userPrefixDao: UserPrefixDao) {

    val prefixCache = Caffeine.newBuilder()
            .expireAfterAccess(FREQUENTLY_USED_CACHE, TimeUnit.MINUTES)
            .executor(taskManager.executorService)
            .buildAsync<Long, List<String>>() { key, executor -> getPrefixes(key, executor) }

    private fun getPrefixes(userId: Long, executor: Executor = taskManager.executorService): CompletableFuture<List<String>> {
        val prefixes = CompletableFuture<List<String>>()
        executor.execute {
            userPrefixDao.get(userId) { prefixesString ->
                val list: List<String> = if (prefixesString == "") emptyList() else prefixesString.split("%SPLIT%")
                prefixes.complete(list)
            }
        }
        return prefixes
    }

    fun addPrefix(userId: Long, prefix: String) {
        val prefixList = prefixCache.get(userId).get().toMutableList()
        if (!prefixList.contains(prefix))
            prefixList.add(prefix)
        setPrefixes(userId, prefixList)
    }

    private fun setPrefixes(userId: Long, prefixList: List<String>) {
        val prefixes = prefixList.joinToString("%SPLIT%")
        userPrefixDao.set(userId, prefixes)
        prefixCache.put(userId, CompletableFuture.completedFuture(prefixList))
    }

    fun removePrefix(userId: Long, prefix: String) {
        val prefixList = prefixCache.get(userId).get().toMutableList()
        prefixList.remove(prefix)
        setPrefixes(userId, prefixList)
    }
}