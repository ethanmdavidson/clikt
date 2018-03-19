package com.github.ajalt.clikt.core

import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.parameters.Argument
import com.github.ajalt.clikt.parameters.options.Option
import com.github.ajalt.clikt.parameters.options.helpOption
import com.github.ajalt.clikt.parsers.Parser
import kotlin.system.exitProcess

abstract class CliktCommand(
        val help: String = "",
        val epilog: String = "",
        name: String? = null,
        val invokeWithoutSubcommand: Boolean = false,
        autoEnvvarPrefix: String? = null) {
    val name = name ?: javaClass.simpleName.split("$").last().toLowerCase()
    internal var subcommands: List<CliktCommand> = emptyList()
    internal val options: MutableList<Option> = mutableListOf()
    internal val arguments: MutableList<Argument> = mutableListOf()
    internal var contextConfig: Context.Builder.() -> Unit = {}
    var autoEnvvarPrefix: String? = autoEnvvarPrefix
        internal set
    private var _context: Context? = null
    val context: Context
        get() {
            checkNotNull(_context) { "Context accessed before parse has been called." }
            return _context!!
        }

    private fun registeredOptionNames() = options.flatMapTo(HashSet()) { it.names }

    private fun createContext(parent: Context? = null) {
        _context = Context.build(this, parent, contextConfig)

        if (context.helpOptionNames.isEmpty()) return
        val names = context.helpOptionNames - registeredOptionNames()
        if (names.isNotEmpty()) options += helpOption(names, context.helpOptionMessage)

        for (command in subcommands) {
            command.createContext(context)
        }
    }

    protected fun shortHelp(): String = help.split(".", "\n", limit = 2).first().trim()

    private fun allHelpParams() = options.mapNotNull { it.parameterHelp } +
            arguments.mapNotNull { it.parameterHelp } +
            subcommands.map { ParameterHelp.Subcommand(it.name, it.shortHelp()) }

    fun registerOption(option: Option) {
        val names = registeredOptionNames()
        for (name in option.names) {
            require(name !in names) { "Duplicate option name $name" }
        }
        options += option
    }

    fun registerArgument(argument: Argument) {
        arguments += argument
    }

    open fun getFormattedUsage(): String {
        if (_context == null) createContext()
        return context.helpFormatter.formatUsage(allHelpParams(), programName = name)
    }

    open fun getFormattedHelp(): String {
        if (_context == null) createContext()
        return context.helpFormatter.formatHelp(help, epilog, allHelpParams(), programName = name)
    }

    open fun aliases(): Map<String, List<String>> = emptyMap()

    fun parse(argv: Array<String>, context: Context? = null) {
        createContext(context)
        Parser.parse(argv, this.context)
    }

    open fun main(argv: Array<String>) {
        try {
            parse(argv)
        } catch (e: PrintHelpMessage) {
            println(e.command.getFormattedHelp())
            exitProcess(0)
        } catch (e: PrintMessage) {
            println(e.message)
            exitProcess(0)
        } catch (e: UsageError) {
            println(e.helpMessage(context))
            exitProcess(1)
        } catch (e: CliktError) {
            println(e.message)
            exitProcess(1)
        } catch (e: Abort) {
            println("Aborted!")
            exitProcess(1)
        }
    }

    abstract fun run()
}

private fun normalizeEnvvar(prefix: String?, name: String): String? = prefix.let {
    return it + "_" + name.replace(Regex("\\W"), "_").toUpperCase()
}

fun <T : CliktCommand> T.subcommands(commands: Iterable<CliktCommand>): T = apply {
    subcommands += commands
    for (sub in commands) {
        if (sub.autoEnvvarPrefix == null) {
            sub.autoEnvvarPrefix = normalizeEnvvar(autoEnvvarPrefix, sub.name)
        }
    }
}

fun <T : CliktCommand> T.subcommands(vararg commands: CliktCommand): T = apply {
    subcommands += commands
    for (sub in commands) {
        if (sub.autoEnvvarPrefix == null) {
            sub.autoEnvvarPrefix = normalizeEnvvar(autoEnvvarPrefix, sub.name)
        }
    }
}

fun <T : CliktCommand> T.context(block: Context.Builder.() -> Unit): T = apply {
    contextConfig = block
}
