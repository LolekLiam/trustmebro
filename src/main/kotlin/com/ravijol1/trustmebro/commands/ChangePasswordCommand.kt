package com.ravijol1.trustmebro.commands

import com.ravijol1.trustmebro.Trustmebro
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ChangePasswordCommand(private val plugin: Trustmebro) : CommandExecutor {
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
        if (args.size < 3) {
            p.sendMessage("§eUsage: §a/changepassword <old> <new> <confirm>")
            return true
        }
        val old = args[0]
        val new = args[1]
        val confirm = args[2]
        if (!plugin.authManager.verify(p.name, old)) {
            p.sendMessage("§cOld password is incorrect.")
            return true
        }
        if (new != confirm) {
            p.sendMessage("§cNew passwords do not match.")
            return true
        }
        if (new.length < 4) {
            p.sendMessage("§cPassword too short. Minimum 4 characters.")
            return true
        }
        plugin.authManager.changePassword(p.name, new)
        val ip = p.address?.address?.hostAddress
        if (ip != null) plugin.authManager.setLastIp(p.name, ip)
        plugin.markAuthenticated(p)
        p.sendMessage("§aPassword changed and you are authenticated.")
        return true
    }
}
