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
import java.util.logging.Filter
import java.util.logging.LogRecord
import java.util.logging.Logger

class Trustmebro : JavaPlugin() {

    lateinit var authManager: AuthManager
        private set

    private val unauthenticated: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

    override fun onEnable() {
        // Install a log filter to suppress sensitive command lines from console
        installSensitiveCommandLogFilter()

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

    private fun installSensitiveCommandLogFilter() {
        val sensitive = setOf("/register", "/login", "/changepassword")

        fun containsSensitive(text: String): Boolean {
            val msg = text.lowercase()
            if (!msg.contains("issued server command")) return false
            return sensitive.any { msg.contains(it) }
        }

        // 1) java.util.logging filter (covers some paths, Spigot-based loggers)
        fun isSensitiveRecord(r: LogRecord): Boolean {
            val base = (r.message ?: "")
            val paramsStr = r.parameters?.joinToString(" ") { it?.toString() ?: "" } ?: ""
            val combined = "$base $paramsStr"
            return containsSensitive(combined)
        }

        val julFilter = Filter { record ->
            if (isSensitiveRecord(record)) return@Filter false
            true
        }

        fun attachJul(logger: Logger?) {
            if (logger == null) return
            try {
                for (handler in logger.handlers) {
                    val existing = handler.filter
                    handler.filter = if (existing == null) {
                        julFilter
                    } else {
                        Filter { rec -> existing.isLoggable(rec) && julFilter.isLoggable(rec) }
                    }
                }
            } catch (_: Throwable) {
                // Best-effort; ignore
            }
        }

        attachJul(Bukkit.getLogger())
        attachJul(Logger.getLogger(""))
        attachJul(logger.parent)

        // 2) Log4j2 filter (covers Paper's main console logger)
        try {
            val logManagerClass = Class.forName("org.apache.logging.log4j.LogManager")
            val loggerContextClass = Class.forName("org.apache.logging.log4j.core.LoggerContext")
            val logEventClass = Class.forName("org.apache.logging.log4j.core.LogEvent")
            val abstractFilterClass = Class.forName("org.apache.logging.log4j.core.filter.AbstractFilter")
            val resultClass = Class.forName("org.apache.logging.log4j.core.Filter\$Result")
            val configurationClass = Class.forName("org.apache.logging.log4j.core.config.Configuration")
            val log4jLoggerConfigClass = Class.forName("org.apache.logging.log4j.core.config.LoggerConfig")

            // Obtain LoggerContext
            val ctx = logManagerClass.getMethod("getContext", Boolean::class.javaPrimitiveType).invoke(null, false)
            if (!loggerContextClass.isInstance(ctx)) return
            val context = ctx

            // Get Configuration
            val getConfiguration = loggerContextClass.getMethod("getConfiguration")
            val config = getConfiguration.invoke(context)
            if (!configurationClass.isInstance(config)) return

            // Build a Filter by extending AbstractFilter
            val filter = java.lang.reflect.Proxy.newProxyInstance(
                abstractFilterClass.classLoader,
                arrayOf(Class.forName("org.apache.logging.log4j.core.Filter"))
            ) { _, method, args ->
                try {
                    val name = method.name
                    if (name == "filter") {
                        // method overloads: (LogEvent), (Logger, Level, Marker, String, Object...), etc.
                        val text = when {
                            args != null && args.isNotEmpty() && logEventClass.isInstance(args[0]) -> {
                                val event = args[0]
                                val message = event.javaClass.getMethod("getMessage").invoke(event)
                                val formatted = message?.javaClass?.getMethod("getFormattedMessage")?.invoke(message) as? String ?: ""
                                formatted
                            }
                            args != null && args.size >= 4 && args[3] is String -> {
                                // (Logger, Level, Marker, String, Object...)
                                val msgTemplate = args[3] as String
                                val params = if (args.size >= 5) (args.copyOfRange(4, args.size).joinToString(" ") { it?.toString() ?: "" }) else ""
                                "$msgTemplate $params"
                            }
                            else -> ""
                        }
                        val deny = containsSensitive(text)
                        return@newProxyInstance if (deny) {
                            // DENY
                            java.lang.Enum.valueOf(resultClass as Class<out Enum<*>>, "DENY")
                        } else {
                            // NEUTRAL
                            java.lang.Enum.valueOf(resultClass as Class<out Enum<*>>, "NEUTRAL")
                        }
                    }
                } catch (_: Throwable) { }
                null
            }

            // Attach to root logger config and all loggers
            val addFilterToLoggerConfig = log4jLoggerConfigClass.getMethod("addFilter", Class.forName("org.apache.logging.log4j.core.Filter"))
            val rootLoggerConfig = configurationClass.getMethod("getRootLogger").invoke(config)
            addFilterToLoggerConfig.invoke(rootLoggerConfig, filter)

            val loggersMap = configurationClass.getMethod("getLoggers").invoke(config) as Map<*, *>
            for (entry in loggersMap.values) {
                if (log4jLoggerConfigClass.isInstance(entry)) {
                    addFilterToLoggerConfig.invoke(entry, filter)
                }
            }

            // Apply updated configuration
            logger.info("Installed Log4j2 filter to hide sensitive commands from console logs.")
            loggerContextClass.getMethod("updateLoggers").invoke(context)
        } catch (_: Throwable) {
            // Log4j2 not available or failed to attach; JUL filter still active.
        }
    }
}
