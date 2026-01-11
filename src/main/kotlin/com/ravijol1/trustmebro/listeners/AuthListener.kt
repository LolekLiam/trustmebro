package com.ravijol1.trustmebro.listeners

import com.ravijol1.trustmebro.Trustmebro
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class AuthListener(private val plugin: Trustmebro) : Listener {

    private val allowedCommands = setOf("/login", "/register", "/changepassword")

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

    @EventHandler
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        val p = e.player
        val raw = e.message.trim()
        val msg = raw.lowercase()
        if (plugin.isAuthenticated(p)) return
        val isAllowed = allowedCommands.any { msg == it || msg.startsWith("$it ") }
        if (!isAllowed) {
            p.sendMessage("§cYou must authenticate. Allowed commands: /register, /login, /changepassword")
            e.isCancelled = true
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
