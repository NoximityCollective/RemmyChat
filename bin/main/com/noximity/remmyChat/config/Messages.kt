package com.noximity.remmyChat.config

import com.noximity.remmyChat.RemmyChat
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class Messages(private val plugin: RemmyChat) {
    private var messagesFile: File? = null
    private var messagesConfig: FileConfiguration? = null

    init {
        loadMessages()
    }

    private fun loadMessages() {
        if (messagesFile == null) {
            messagesFile = File(plugin.getDataFolder(), "messages.yml")
        }

        if (!messagesFile!!.exists()) {
            plugin.saveResource("messages.yml", false)
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile!!)

        try {
            plugin.getResource("messages.yml").use { defaultStream ->
                if (defaultStream != null) {
                    val defaultConfig: YamlConfiguration = YamlConfiguration.loadConfiguration(
                        InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                    )
                    messagesConfig!!.setDefaults(defaultConfig)
                }
            }
        } catch (e: IOException) {
            plugin.getLogger().severe("Could not load default messages.yml: " + e.message)
        }
    }

    fun reloadMessages() {
        loadMessages()
    }

    fun getMessage(path: String): String {
        return messagesConfig!!.getString(path, "Message not found: " + path)!!
    }

    fun getMessagesConfig(): FileConfiguration {
        return messagesConfig!!
    }
}