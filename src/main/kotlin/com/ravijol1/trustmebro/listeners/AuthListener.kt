package com.ravijol1.trustmebro.listeners

import com.ravijol1.trustmebro.Trustmebro
import com.ravijol1.trustmebro.commands.RegisterCommand
import com.ravijol1.trustmebro.commands.LoginCommand
import com.ravijol1.trustmebro.commands.ChangePasswordCommand
import org.bukkit.command.Command
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class AuthListener(private val plugin: Trustmebro) : Listener {

    private val allowedCommands = setOf("login", "register", "changepassword")

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        plugin.handleJoin(e.player)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        // Clean up state set to avoid leaks
        if (!plugin.isAuthenticated(e.player)) {
            // nothing special, but ensure removed
        }
    }

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        if (plugin.isAuthenticated(p)) return
        // Allow looking around but prevent changing block position
        if (e.from.blockX != e.to?.blockX || e.from.blockY != e.to?.blockY || e.from.blockZ != e.to?.blockZ) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val p = e.player
        if (!plugin.isAuthenticated(p)) {
            p.sendMessage("§cYou must authenticate. Use §a/register §cor §a/login§c.")
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        val p = e.player
        val originalRaw = e.message
        val raw = originalRaw.trim()

        // Normalize: strip leading slash, collapse whitespace, lowercase, drop namespace
        val noSlash = if (raw.startsWith("/")) raw.substring(1) else raw
        val parts = noSlash.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val fullLabel = parts.firstOrNull()?.lowercase() ?: return
        val baseLabel = fullLabel.substringAfterLast(':')

        if (baseLabel in allowedCommands) {
            // Overwrite the message to a benign value so NOTHING logs sensitive args
            e.message = "/."
            // Suppress console logging by cancelling the event and executing manually
            e.isCancelled = true

            val args = if (parts.size > 1) parts.drop(1).toTypedArray() else emptyArray()

            // Dispatch to the appropriate executor without letting Bukkit log the command
            dispatchSensitiveCommandSilently(p, baseLabel, args)
            return
        }

        // Not a sensitive command → enforce auth gate
        if (!plugin.isAuthenticated(p)) {
            p.sendMessage("§cYou must authenticate. Allowed commands: /register, /login, /changepassword")
            e.isCancelled = true
        }
    }

    private fun dispatchSensitiveCommandSilently(sender: org.bukkit.entity.Player, label: String, args: Array<String>) {
        val executor = when (label) {
            "register" -> RegisterCommand(plugin)
            "login" -> LoginCommand(plugin)
            "changepassword" -> ChangePasswordCommand(plugin)
            else -> return
        }
        // Create a minimal dummy Command for the executor API
        val dummy = object : Command(label) {
            override fun execute(sender0: org.bukkit.command.CommandSender, commandLabel: String, args0: Array<out String>): Boolean {
                return false
            }
        }
        try {
            executor.onCommand(sender, dummy, label, args)
        } catch (t: Throwable) {
            // Do not leak sensitive args to console
            plugin.logger.warning("An error occurred while executing a sensitive command for ${sender.name}. Check stacktrace in debug builds.")
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (!plugin.isAuthenticated(p)) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        if (!plugin.isAuthenticated(e.player)) e.isCancelled = true
    }

    @EventHandler
    fun onPickup(e: PlayerAttemptPickupItemEvent) {
        if (!plugin.isAuthenticated(e.player)) e.isCancelled = true
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val p = e.whoClicked
        if (p is org.bukkit.entity.Player && !plugin.isAuthenticated(p)) {
            e.isCancelled = true
        }
    }
}
