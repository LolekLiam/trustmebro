package com.ravijol1.trustmebro.commands

import com.ravijol1.trustmebro.Trustmebro
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RegisterCommand(private val plugin: Trustmebro) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players.")
            return true
        }
        val p = sender
        if (plugin.authManager.isRegistered(p.name)) {
            p.sendMessage("§cYou are already registered. Use §a/login <password> §cor §a/changepassword <old> <new> <confirm>§c.")
            return true
        }
        if (args.size < 2) {
            p.sendMessage("§eUsage: §a/register <password> <confirm>")
            return true
        }
        val pass = args[0]
        val confirm = args[1]
        if (pass != confirm) {
            p.sendMessage("§cPasswords do not match.")
            return true
        }
        if (pass.length < 4) {
            p.sendMessage("§cPassword too short. Minimum 4 characters.")
            return true
        }
        plugin.authManager.register(p.name, pass)
        val ip = p.address?.address?.hostAddress
        if (ip != null) plugin.authManager.setLastIp(p.name, ip)
        plugin.markAuthenticated(p)
        p.sendMessage("§aRegistered and authenticated. Welcome!")
        return true
    }
}
