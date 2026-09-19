package com.naki.skiff.link

/**
 * Builds the `skiffcode://` links Skiff hands to Skiff Code. The reading side is `:code`'s
 * `SkiffCodeUri`, whose tests parse what this builds, so the two cannot drift apart.
 *
 * ```
 * remote  skiffcode://alice@192.0.2.10:22/home/alice/demo/a.md?alias=home-server
 * local   skiffcode:///storage/emulated/0/Documents/a.md
 * ```
 */
object SkiffCodeLink {

    fun local(path: String): String = "skiffcode://${encode(path, keepSlash = true)}"

    /** [alias] is the profile's name, which Skiff Code tries before (user, host, port). */
    fun remote(user: String, host: String, port: Int, path: String, alias: String?): String {
        val hostPart = if (':' in host) "[$host]" else encode(host, keepSlash = false)
        val query = alias?.let { "?alias=${encode(it, keepSlash = false)}" } ?: ""
        return "skiffcode://${encode(user, keepSlash = false)}@$hostPart:$port${encode(path, keepSlash = true)}$query"
    }

    // Everything but RFC 3986's unreserved characters goes as UTF-8 percent escapes, so a Korean
    // name, a space or a `?`/`#`/`&` in a path cannot end the path or the query early.
    private fun encode(text: String, keepSlash: Boolean): String = buildString {
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~" || (keepSlash && c == '/'))) append(c)
            else append('%').append("%02X".format(byte.toInt() and 0xFF))
        }
    }
}
