package com.ravijol1.trustmebro.commands

import com.ravijol1.trustmebro.Trustmebro
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class LoginCommand(private val plugin: Trustmebro) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players.")
            return true
        }
        val p = sender
        if (!plugin.authManager.isRegistered(p.name)) {
            p.sendMessage("§cYou are not registered. Use §a/register <password> <confirm>§c.")
            return true
        }
        if (args.isEmpty()) {
            p.sendMessage("§eUsage: §a/login <password>")
            return true
        }
        val pass = args[0]
        if (!plugin.authManager.verify(p.name, pass)) {
            p.sendMessage("§cInvalid password.")
            return true
        }
        val ip = p.address?.address?.hostAddress
        if (ip != null) plugin.authManager.setLastIp(p.name, ip)
        plugin.markAuthenticated(p)
        p.sendMessage("§aSuccessfully authenticated. Have fun!")
        return true
    }
}
