package com.ravijol1.trustmebro

import com.ravijol1.trustmebro.auth.AuthManager
import com.ravijol1.trustmebro.commands.ChangePasswordCommand
import com.ravijol1.trustmebro.commands.LoginCommand
import com.ravijol1.trustmebro.commands.RegisterCommand
import com.ravijol1.trustmebro.listeners.AuthListener
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getServer
import org.bukkit.command.CommandExecutor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
// import org.bukkit.command.PluginCommand
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Trustmebro : JavaPlugin() {

    lateinit var authManager: AuthManager
        private set

    private val unauthenticated: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

    override fun onEnable() {
        authManager = AuthManager(this)
        // Register listeners
        server.pluginManager.registerEvents(AuthListener(this), this)

        // Programmatically register commands (no YAML) using reflection
        registerCommandReflect("register", "Register a password for your account", "/register <password> <confirm>", RegisterCommand(this))
        registerCommandReflect("login", "Authenticate with your password when joining from a new IP", "/login <password>", LoginCommand(this))
        registerCommandReflect("changepassword", "Change your account password", "/changepassword <old> <new> <confirm>", ChangePasswordCommand(this))

        // Handle reloads: ensure players with mismatched IPs are gated
        for (player in Bukkit.getOnlinePlayers()) {
            handleJoin(player)
        }
    }

    override fun onDisable() {
        // Nothing special
    }

    fun isAuthenticated(player: Player): Boolean = !unauthenticated.contains(player.uniqueId)

    fun markAuthenticated(player: Player) {
        unauthenticated.remove(player.uniqueId)
    }

    fun markUnauthenticated(player: Player) {
        unauthenticated.add(player.uniqueId)
    }

    fun handleJoin(player: Player) {
        val username = player.name
        val ip = player.address?.address?.hostAddress
        if (ip == null) {
            // Edge case: no IP? require auth just in case
            markUnauthenticated(player)
            player.sendMessage("§cUnable to detect your IP. Please /login or /register.")
            return
        }
        if (!authManager.isRegistered(username)) {
            markUnauthenticated(player)
            player.sendMessage("§eWelcome, §f$username§e! This server uses password auth.")
            player.sendMessage("§7Use §a/register <password> <confirm> §7to set your password.")
        } else {
            val lastIp = authManager.getLastIp(username)
            if (lastIp != null && lastIp == ip) {
                markAuthenticated(player)
                player.sendMessage("§aAuthenticated by IP. Welcome back!")
            } else {
                markUnauthenticated(player)
                player.sendMessage("§eNew IP detected. Please §a/login <password> §eto continue.")
            }
        }
    }

    private fun registerCommandReflect(
        name: String,
        description: String,
        usage: String,
        executor: CommandExecutor
    ) {
        try {
            val command = object : Command(name) {
                init {
                    this.description = description
                    this.usageMessage = usage
                }
                override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
                    return executor.onCommand(sender, this, label, args)
                }
            }

            val server = getServer()
            val commandMapField = server.javaClass.getDeclaredField("commandMap")
            commandMapField.isAccessible = true
            val commandMap = commandMapField.get(server) as org.bukkit.command.CommandMap
            commandMap.register(this.name, command)
        } catch (t: Throwable) {
            logger.severe("Failed to register command '$name': ${t.message}")
        }
    }
}
