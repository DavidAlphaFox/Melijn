package me.melijn.melijnbot.database.prefix

import me.melijn.melijnbot.database.Dao
import me.melijn.melijnbot.database.DriverManager

class GuildPrefixDao(driverManager: DriverManager) : Dao(driverManager) {

    override val table: String = "guildPrefixes"
    override val tableStructure: String = "guildId bigInt, prefixes varchar(256)"
    override val keys: String = "PRIMARY KEY(guildId)"

    init {
        driverManager.registerTable(table, tableStructure, keys)
    }

    fun set(guildId: Long, prefixes: String) {
        driverManager.executeUpdate("INSERT INTO $table (guildId, prefixes) VALUES (?, ?) ON DUPLICATE KEY UPDATE prefixes = ?",
                guildId, prefixes, prefixes)
    }

    fun get(guildId: Long, prefixes: (String) -> Unit) {
        driverManager.executeQuery("SELECT * FROM $table WHERE guildId = ?", { resultSet ->
            if (resultSet.next()) {
                prefixes.invoke(resultSet.getString("prefixes"))
            } else prefixes.invoke("")
        }, guildId)
    }
}