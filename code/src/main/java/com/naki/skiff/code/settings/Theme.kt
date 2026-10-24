package com.naki.skiff.code.settings

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * A theme's colors, keyed `section.name` the way the file spells them (`ui.background`), which the
 * page turns into one CSS variable each (docs/skiffcode.spec/settings-theme.md). [dark] is the `base`
 * the theme is drawn over: what fills in a color it lacks, and which way the system bars go.
 */
data class Theme(val dark: Boolean, val colors: Map<String, String>) {

    /** [key]'s color as Android's ARGB, for the window around the page. CSS puts alpha last; Android first. */
    fun argb(key: String): Int {
        val hex = colors.getValue(key).removePrefix("#")
        val full = if (hex.length <= 4) hex.flatMap { listOf(it, it) }.joinToString("") else hex
        val rgb = full.take(6).toLong(16)
        val alpha = if (full.length == 8) full.substring(6).toLong(16) else 0xff
        return ((alpha shl 24) or rgb).toInt()
    }
}

/** The theme to use, and what in its file was passed over to arrive at it. */
data class ThemeRead(val theme: Theme, val problems: List<SettingsProblem>)

object ThemeToml {

    /**
     * Every color a theme has. The page reads each as a variable, so a key outside this list would
     * reach nothing — and is most likely one of these, misspelled.
     */
    val KEYS: List<String> = listOf(
        "ui.background", "ui.foreground", "ui.muted", "ui.border", "ui.faint", "ui.subtle", "ui.accent",
        "ui.scrim", "ui.strong_background", "ui.strong_foreground",
        "editor.background", "editor.foreground", "editor.gutter_background", "editor.gutter_foreground",
        "editor.gutter_border", "editor.cursor", "editor.selection", "editor.code_background",
        "syntax.keyword", "syntax.atom", "syntax.literal", "syntax.string", "syntax.regexp",
        "syntax.definition", "syntax.local", "syntax.type", "syntax.class", "syntax.macro",
        "syntax.property", "syntax.comment", "syntax.meta", "syntax.invalid", "syntax.warning",
        "diff.added", "diff.removed", "diff.changed",
    )

    /** What CSS reads as a color without asking anything else: no names, no functions. */
    internal val COLOR = Regex("#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

    @Serializable
    private data class ThemeFile(
        val base: String = "light",
        val ui: Map<String, String> = emptyMap(),
        val editor: Map<String, String> = emptyMap(),
        val syntax: Map<String, String> = emptyMap(),
        val diff: Map<String, String> = emptyMap(),
    )

    /**
     * Reads [text]. The same rules as `settings.toml`: a file that does not read, or names a color
     * there is none of, gives the base's colors whole; one that reads keeps every color it can and
     * takes the base's for each one it cannot. [fallback] gives the base's colors — the bundled
     * theme on that side.
     */
    fun read(text: String, fallback: (dark: Boolean) -> Map<String, String>): ThemeRead {
        val file = try {
            Toml.decodeFromString(ThemeFile.serializer(), text)
        } catch (e: SerializationException) {
            return ThemeRead(Theme(false, fallback(false)), listOf(SettingsToml.problemOf(e)))
        }
        val problems = mutableListOf<SettingsProblem>()
        val dark = when (file.base) {
            "light" -> false
            "dark" -> true
            else -> {
                problems += SettingsProblem.Refused("base", "\"${file.base}\"", "\"light\"", null)
                false
            }
        }
        val base = fallback(dark)
        val given = mapOf("ui" to file.ui, "editor" to file.editor, "syntax" to file.syntax, "diff" to file.diff)
            .flatMap { (section, table) -> table.map { (name, value) -> "$section.$name" to value } }
            .toMap()
        given.keys.firstOrNull { it !in KEYS }?.let {
            return ThemeRead(Theme(dark, base), problems + SettingsProblem.UnknownKey(it))
        }
        val colors = KEYS.mapNotNull { key ->
            val value = given[key]
            when {
                value == null -> base[key]
                COLOR.matches(value) -> value
                else -> {
                    problems += SettingsProblem.Refused(key, "\"$value\"", "\"${base[key]}\"", null)
                    base[key]
                }
            }?.let { key to it }
        }.toMap()
        return ThemeRead(Theme(dark, colors), problems)
    }
}

/**
 * One line of `assets/themes/index.json`, the list of what the app ships
 * (docs/skiffcode.spec/community-themes.md): the id `settings.toml` names it by and its file is called,
 * the name the palette shows, the collection it comes from and its `base`.
 */
@Serializable
data class BundledTheme(val id: String, val title: String, val family: String, val base: String)

/**
 * A theme as the palette's theme list draws it: [swatch] is [Themes.SWATCH]'s colors, in that order.
 * `system` carries the colors of the bundled theme it follows now.
 */
data class ThemeEntry(val id: String, val title: String, val dark: Boolean, val swatch: List<String>)

/**
 * The themes the app ships, `assets/themes/<id>.toml` as `index.json` lists them, and the person's own —
 * brought in with Import Theme or made with Copy Theme — `<dir>/<name>.toml`
 * (docs/skiffcode.spec/settings-theme.md). Each is read once, the first time it is asked for. The
 * bundled ones are read against nothing — `ThemeTomlTest` holds them to having every key — and one of
 * the person's own against the bundled theme on its `base` side.
 *
 * A file of the person's own with a bundled theme's id wins over the bundled one, which is then not
 * offered (2026-10-22 user's decision): a theme an update starts shipping does not take their file's
 * place. `light` and `dark` cannot be shadowed, since no file of theirs can be called that.
 */
class Themes(private val open: (path: String) -> InputStream, private val dir: File) {

    private val index: List<BundledTheme> by lazy {
        Json.decodeFromString<List<BundledTheme>>(open("themes/index.json").use { it.readBytes().decodeToString() })
    }

    private val bundled: Map<String, Lazy<Theme>> by lazy {
        index.associate { entry -> entry.id to lazy { bundledRead(entry.id) } }
    }

    /** The bundled ids in lowercase, which no theme of the person's own can newly take. */
    private val reserved: Set<String> by lazy { index.map { it.id.lowercase() }.toSet() }

    private val imported = ConcurrentHashMap<String, Theme>()

    @Volatile
    private var importedNames: List<String>? = null

    private val own: List<String>
        get() = importedNames ?: listImported().also { importedNames = it }

    /**
     * What `settings.toml`'s `theme` may say, in the palette's order: `system`, `light` and `dark`, the
     * rest of the bundled themes by the name they show, then the person's own.
     */
    val names: List<String>
        get() {
            val own = own
            val shadowed = own.map { it.lowercase() }.toSet()
            val (bases, rest) = index.partition { it.id in BASES }
            val offered = bases + rest.filter { it.id.lowercase() !in shadowed }.sortedBy { it.title.lowercase() }
            return listOf(SettingsToml.SYSTEM_THEME) + offered.map { it.id } + own
        }

    /** Every theme [names] offers, with what the palette draws it by, `system` as it is under [night]. */
    fun entries(night: Boolean): List<ThemeEntry> {
        val titles = index.associate { it.id to it.title }
        return names.map { id ->
            val theme = resolve(id, night)
            val title = when {
                id == SettingsToml.SYSTEM_THEME -> "System"
                isImported(id) -> id
                else -> titles.getValue(id)
            }
            ThemeEntry(id, title, theme.dark, SWATCH.map { theme.colors.getValue(it) })
        }
    }

    private fun listImported(): List<String> =
        dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }.orEmpty()
            .map { it.name.removeSuffix(SUFFIX) }.filter { fileNameOf(it + SUFFIX) == it }.sorted()

    private fun bundledRead(name: String): Theme =
        ThemeToml.read(bundledText(name)) { emptyMap() }.theme

    private fun bundledText(name: String) = open("themes/$name$SUFFIX").use { it.readBytes().decodeToString() }

    private fun read(text: String): ThemeRead =
        ThemeToml.read(text) { dark -> bundled.getValue(if (dark) "dark" else "light").value.colors }

    /** [choice] as `settings.toml` says it, where `system` is the bundled theme that matches [night]. */
    fun resolve(choice: String, night: Boolean): Theme {
        val name = nameIn(choice, night)
        if (!isImported(name)) bundled[name]?.let { return it.value }
        return imported.getOrPut(name) { read(file(name).readText()).theme }
    }

    /** The theme [choice] shows under [night]: itself, or for `system` the bundled one it follows. */
    fun nameIn(choice: String, night: Boolean): String =
        if (choice == SettingsToml.SYSTEM_THEME) (if (night) "dark" else "light") else choice

    /**
     * Whether [name] is a theme of the person's own — one Import Theme would replace, and one that can
     * be edited and deleted. Not `system`, which names a bundled theme.
     */
    fun isImported(name: String): Boolean = name in own

    /** The theme of the person's own that [name] is in any case — the one a download of [name] replaces. */
    fun ownNamed(name: String): String? = own.firstOrNull { it.equals(name, ignoreCase = true) }

    /**
     * The theme a file called [fileName] comes in as, by Import Theme or Copy Theme: its name without
     * `.toml`, or null when that is not a name a theme can have — including the ones `settings.toml`
     * already means something by and every bundled theme's id, in any case.
     */
    fun nameOf(fileName: String): String? = fileNameOf(fileName)?.takeIf { it.lowercase() !in reserved }

    /**
     * Keeps [text] as the theme [name] — as it was written, so exporting it gives the person their own
     * file back — and says what in it was passed over. [name] is one [nameOf] gave.
     */
    fun import(name: String, text: String): List<SettingsProblem> {
        val read = read(text)
        dir.mkdirs()
        file(name).writeText(text)
        imported[name] = read.theme
        importedNames = null
        return read.problems
    }

    /** The file behind the theme [name], as Export Theme writes it out. */
    fun text(name: String): String = if (isImported(name)) file(name).readText() else bundledText(name)

    /** Where the theme [name] is kept when it is the person's own, which Edit Theme opens. */
    fun file(name: String): File = File(dir, name + SUFFIX)

    /** The theme a file at [path] is, when it is one of the person's own; null for any other file. */
    fun ownAt(path: String): String? {
        val file = File(path)
        if (file.parentFile?.path != dir.path) return null
        return fileNameOf(file.name)?.takeIf { isImported(it) && file(it).path == path }
    }

    /**
     * Reads the theme [name] from its file again, which is what saving it in the app does, and says
     * what in it was passed over.
     */
    fun reread(name: String): List<SettingsProblem> {
        val read = read(file(name).readText())
        imported[name] = read.theme
        return read.problems
    }

    /** Removes the theme [name]. Only one of the person's own: a bundled theme is part of the app. */
    fun delete(name: String) {
        require(isImported(name)) { "not a theme of the person's own: $name" }
        file(name).delete()
        imported.remove(name)
        importedNames = null
    }

    /** Removes every theme of the person's own, as Reset Data does, leaving the bundled ones. */
    fun deleteAllImported() {
        dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }.orEmpty().forEach { it.delete() }
        imported.clear()
        importedNames = null
    }

    companion object {
        const val SUFFIX = ".toml"

        /** The bundled themes `system` follows, which come first and which nothing can shadow. */
        private val BASES = listOf("light", "dark")

        /** The colors of a theme the palette's list draws, the background first. */
        val SWATCH = listOf(
            "editor.background", "editor.foreground", "syntax.keyword", "syntax.string", "syntax.definition", "ui.accent",
        )

        /**
         * Letters (any script), digits, spaces, `_`, `-` and parentheses — the last so that a copy
         * the file picker named `ocean (1).toml` still comes in. Nothing that reaches outside the
         * directory, nothing TOML would need to escape in `theme = "…"`.
         */
        private val NAME = Regex("""[\p{L}\p{N}][\p{L}\p{N} _()-]{0,63}""")

        /**
         * The name a file called [fileName] has as a theme of the person's own, already brought in: its
         * name without `.toml`, or null when that is not a name a theme can have — including the ones
         * `settings.toml` already means something by. A bundled id passes, so a file kept from before
         * the app shipped that theme is still theirs.
         */
        private fun fileNameOf(fileName: String): String? {
            val name = (if (fileName.endsWith(SUFFIX, ignoreCase = true)) fileName.dropLast(SUFFIX.length) else fileName).trim()
            if (!NAME.matches(name)) return null
            if (name.lowercase() in setOf(SettingsToml.SYSTEM_THEME) + BASES) return null
            return name
        }
    }
}
