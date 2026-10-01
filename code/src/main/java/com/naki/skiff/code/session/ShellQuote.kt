package com.naki.skiff.code.session

/**
 * Turns an argv into one line a POSIX shell reads back as the same argv. The server's sshd hands an
 * `exec` request to the login shell as a string, so every argument has to survive that shell, and a
 * path in a link is input from outside.
 *
 * Each argument goes in single quotes, where nothing is special, and a single quote inside is closed,
 * escaped and reopened (`'\''`). A NUL cannot be passed in an argument at all, so it is refused.
 */
object ShellQuote {

    fun quote(arg: String): String {
        require('\u0000' !in arg) { "an argument cannot hold a NUL" }
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    fun command(argv: List<String>): String {
        require(argv.isNotEmpty()) { "a command needs at least a program" }
        return argv.joinToString(" ") { quote(it) }
    }
}
