package com.naki.skiff.code.settings

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/**
 * What `settings.toml` holds: what the person chose, as opposed to what the page noticed as it was
 * used, which stays in its `localStorage` (docs/skiffcode.spec.md "상태 저장"). Only the keys that
 * something reads today are here; the language servers join with the feature that reads them. A key left out of the file takes the default written here, and
 * [SettingsToml.KEYS] says the same thing in the file a person opens — `SettingsTomlTest` holds the two to it.
 */
@Serializable
data class Settings(
    val editor: Editor = Editor(),
    val files: Files = Files(),
    val search: Search = Search(),
) {
    @Serializable
    data class Editor(
        /** A CSS font family, so a font the device lacks falls back the way any page's would. */
        val font: String = "monospace",
        /** What Reset Zoom returns to. The size a pinch left is the page's, not this. */
        @SerialName("font_size") val fontSize: Int = 14,
        @SerialName("tab_size") val tabSize: Int = 4,
        /** On, because a phone's cover screen is too narrow to read code that runs off to the right. */
        val wrap: Boolean = true,
        /** A bundled or imported theme's name, or `system` for the one that matches the device's dark mode. */
        val theme: String = SettingsToml.SYSTEM_THEME,
        /** How strongly the diff layer tints its lines, in percent. */
        @SerialName("diff_alpha") val diffAlpha: Int = 30,
    )

    @Serializable
    data class Files(
        /** Larger than this is refused rather than read. */
        @SerialName("max_size_mb") val maxSizeMb: Int = 2,
        /** How many files off the screen keep their undo history; see the page's `memories.ts`. */
        @SerialName("kept_buffers") val keptBuffers: Int = 30,
        /** How often the file on the screen is looked at for a change made somewhere else. */
        @SerialName("poll_seconds") val pollSeconds: Int = 2,
    ) {
        val maxSizeBytes: Long get() = maxSizeMb * 1024L * 1024
        val pollMillis: Long get() = pollSeconds * 1000L
    }

    /** The palette's file mode in a project whose server runs no git, which walks the folders instead. */
    @Serializable
    data class Search(
        /** Folders by name that the walk does not go into. `.git` is never gone into, listed or not. */
        @SerialName("skip_dirs") val skipDirs: List<String> = SettingsToml.SKIP_DIRS,
        /** How many files the walk collects before it stops. */
        @SerialName("max_files") val maxFiles: Int = 5000,
    )
}

/** Something in the file that was not used, and what was used instead. */
sealed interface SettingsProblem {
    /** The file as a whole did not read — broken TOML, a word where a number goes, a key we do not know. */
    data class Unreadable(val reason: String) : SettingsProblem

    /** A key we do not know, most likely one we do, misspelled. Also leaves every default. */
    data class UnknownKey(val key: String) : SettingsProblem

    /** One value that read but cannot be used; [allowed] is null where it is not a range. */
    data class Refused(val key: String, val value: String, val default: String, val allowed: IntRange?) : SettingsProblem
}

/** The settings to use, and what in the file was passed over to arrive at them. */
data class SettingsRead(val settings: Settings, val problems: List<SettingsProblem>)

object SettingsToml {

    /** Font sizes the page can show; the same bounds as its pinch (`zoom.ts`). */
    val FONT_SIZES = 8..40
    val TAB_SIZES = 1..16
    val MAX_SIZES_MB = 1..64
    val KEPT_BUFFERS = 0..200
    val POLL_SECONDS = 1..60
    val DIFF_ALPHAS = 0..100
    val MAX_FILES = 100..100_000

    /** What a project without git usually holds that nobody searches for: installed packages and build output. */
    val SKIP_DIRS = listOf("node_modules", ".venv", "venv", "__pycache__", ".gradle", "build", "dist", "target")

    /** The theme that follows the device's dark mode rather than being one of its own. */
    const val SYSTEM_THEME = "system"
    /** The themes every install has; [read] is told of any imported beside them. */
    val THEMES = setOf(SYSTEM_THEME, "light", "dark")

    /**
     * Reads [text]. A file that does not read at all gives every default, since there is no telling
     * which of its values were meant; one that reads keeps every value it can and puts the default
     * in place of each one it cannot. Either way the problems say what was passed over, so nothing
     * the person wrote is ignored without them hearing about it.
     *
     * A key that is not known is a failure rather than something skipped, because the likeliest
     * one is a misspelling of a key that is — and skipping it would look like the setting not working.
     *
     * [themes] is every name `theme` may say, imported themes included.
     */
    fun read(text: String, themes: Collection<String> = THEMES): SettingsRead {
        val parsed = try {
            Toml.decodeFromString(Settings.serializer(), withoutEmptyTables(text))
        } catch (e: SerializationException) {
            // Every ktoml failure is one, with the line in its message: a type that does not fit,
            // an Int that overflows, a key it does not know, a line that is not TOML.
            return SettingsRead(Settings(), listOf(problemOf(e)))
        }
        val problems = mutableListOf<SettingsProblem>()
        val defaults = Settings()

        fun inRange(key: String, value: Int, allowed: IntRange, default: Int): Int {
            if (value in allowed) return value
            problems += SettingsProblem.Refused(key, value.toString(), default.toString(), allowed)
            return default
        }

        val editor = parsed.editor
        val files = parsed.files
        val search = parsed.search
        val font = if (editor.font.isNotBlank()) editor.font else {
            problems += SettingsProblem.Refused("editor.font", "\"${editor.font}\"", "\"${defaults.editor.font}\"", null)
            defaults.editor.font
        }
        val theme = if (editor.theme in themes) editor.theme else {
            problems += SettingsProblem.Refused("editor.theme", "\"${editor.theme}\"", "\"${defaults.editor.theme}\"", null)
            defaults.editor.theme
        }
        // A name with a `/` in it would never match a folder's name, so it would be skipped without a word.
        val skipDirs = if (search.skipDirs.none { it.isEmpty() || '/' in it }) search.skipDirs else {
            problems += SettingsProblem.Refused("search.skip_dirs", toml(search.skipDirs), toml(defaults.search.skipDirs), null)
            defaults.search.skipDirs
        }
        val settings = Settings(
            editor = Settings.Editor(
                font = font,
                fontSize = inRange("editor.font_size", editor.fontSize, FONT_SIZES, defaults.editor.fontSize),
                tabSize = inRange("editor.tab_size", editor.tabSize, TAB_SIZES, defaults.editor.tabSize),
                wrap = editor.wrap,
                theme = theme,
                diffAlpha = inRange("editor.diff_alpha", editor.diffAlpha, DIFF_ALPHAS, defaults.editor.diffAlpha),
            ),
            files = Settings.Files(
                maxSizeMb = inRange("files.max_size_mb", files.maxSizeMb, MAX_SIZES_MB, defaults.files.maxSizeMb),
                keptBuffers = inRange("files.kept_buffers", files.keptBuffers, KEPT_BUFFERS, defaults.files.keptBuffers),
                pollSeconds = inRange("files.poll_seconds", files.pollSeconds, POLL_SECONDS, defaults.files.pollSeconds),
            ),
            search = Settings.Search(
                skipDirs = skipDirs,
                maxFiles = inRange("search.max_files", search.maxFiles, MAX_FILES, defaults.search.maxFiles),
            ),
        )
        return SettingsRead(settings, problems)
    }

    /** A list of names as TOML writes it. */
    private fun toml(names: List<String>): String = names.joinToString(", ", "[", "]") { "\"$it\"" }

    /**
     * A key ktoml did not know is named only inside its message — "Unknown key received:
     * <font_szie> in scope <editor>. Switch the configuration option: …" — whose exception class is
     * internal, and the rest of which is advice for whoever wrote this class. So the message is what
     * is matched, and one in any other shape is passed on whole.
     */
    internal fun problemOf(e: SerializationException): SettingsProblem {
        val found = UNKNOWN_KEY.find(e.message.orEmpty())
            ?: return SettingsProblem.Unreadable(e.message ?: e.javaClass.simpleName)
        val (key, scope) = found.destructured
        return SettingsProblem.UnknownKey(if (scope == "rootNode") key else "$scope.$key")
    }

    /**
     * [text] with the header of each table that sets nothing — every key commented out, as
     * [TEMPLATE] has them — blanked, since ktoml refuses a table without children ("missing children
     * in a table"). Blanked rather than removed, so the line numbers in its messages stay the person's.
     */
    private fun withoutEmptyTables(text: String): String {
        val lines = text.lines().toMutableList()
        for (i in lines.indices) {
            if (!ANY_HEADER.matches(lines[i])) continue
            val end = (i + 1..lines.lastIndex).firstOrNull { ANY_HEADER.matches(lines[it]) } ?: lines.size
            if ((i + 1 until end).all { lines[it].isBlank() || lines[it].trimStart().startsWith("#") }) lines[i] = ""
        }
        return lines.joinToString("\n")
    }

    private val UNKNOWN_KEY = Regex("Unknown key received: <([^>]*)> in scope <([^>]*)>")

    /**
     * [text] with `[editor]`'s `theme` set to [name], for the palette's Theme commands. Only that
     * line changes, so the person's comments and the rest of their values stay as they wrote them.
     * Where the line is commented out, as [TEMPLATE] has it, it is the one written; a file without
     * either gets it at the top of `[editor]`, and one without that table gets both at the end.
     */
    fun withTheme(text: String, name: String): String {
        val line = "theme = \"$name\""
        val lines = text.lines().toMutableList()
        val header = lines.indexOfFirst { EDITOR_HEADER.matches(it) }
        if (header < 0) return text.trimEnd('\n').let { if (it.isEmpty()) "" else "$it\n\n" } + "[editor]\n$line\n"
        val end = (header + 1..lines.lastIndex).firstOrNull { ANY_HEADER.matches(lines[it]) } ?: lines.size
        val existing = (header + 1 until end).firstOrNull { THEME_LINE.matches(lines[it]) }
            ?: (header + 1 until end).firstOrNull { keyLine("theme").matches(lines[it]) }
        if (existing != null) lines[existing] = line else lines.add(header + 1, line)
        return lines.joinToString("\n")
    }

    private val EDITOR_HEADER = Regex("""\s*\[\s*editor\s*]\s*(#.*)?""")
    private val ANY_HEADER = Regex("""\s*\[.*""")
    private val THEME_LINE = Regex("""\s*theme\s*=.*""")

    /** A line that sets [name], or would with its `#` taken off. */
    private fun keyLine(name: String) = Regex("""\s*#?\s*${Regex.escape(name)}\s*=.*""")

    private fun tableHeader(table: String) = Regex("""\s*\[\s*${Regex.escape(table)}\s*]\s*(#.*)?""")

    /** One key as the file shows it: its table, its default as TOML, and what it does. */
    class Key(val table: String, val name: String, val default: String, val about: List<String>) {
        /** What it does, ending with its default, then the key at that default, commented out. */
        val lines: List<String>
            get() = about.dropLast(1).map { "# $it" } + "# ${about.last()} Default: $default." + "# $name = $default"
    }

    /**
     * Every key, in the order the file shows them. A key in [Settings] has to be here as well, or the
     * file never shows it — `SettingsTomlTest` checks the two against each other.
     */
    val KEYS = listOf(
        Key("editor", "font", "\"monospace\"", listOf("A CSS font family. A font the device does not have falls back to the next one listed.")),
        Key("editor", "font_size", "14", listOf("${FONT_SIZES.first} to ${FONT_SIZES.last}. What Reset Zoom returns to; a pinch changes only what you are looking at now.")),
        Key("editor", "tab_size", "4", listOf("${TAB_SIZES.first} to ${TAB_SIZES.last}. How wide a tab character is drawn.")),
        Key("editor", "wrap", "true", listOf("Wrap long lines at the edge of the screen instead of scrolling sideways.")),
        Key(
            "editor", "theme", "\"$SYSTEM_THEME\"",
            listOf(
                "\"system\" follows the device's dark mode; \"light\" and \"dark\" stay put. A theme brought in",
                "with Import Theme goes by its file's name. The palette's Theme commands write this line.",
            ),
        ),
        Key("editor", "diff_alpha", "30", listOf("${DIFF_ALPHAS.first} to ${DIFF_ALPHAS.last}. How strongly the diff layer tints added and deleted lines, in percent.")),
        Key("files", "max_size_mb", "2", listOf("${MAX_SIZES_MB.first} to ${MAX_SIZES_MB.last}. A larger file is not opened.")),
        Key("files", "kept_buffers", "30", listOf("${KEPT_BUFFERS.first} to ${KEPT_BUFFERS.last}. How many files off the screen keep their undo history.")),
        Key("files", "poll_seconds", "2", listOf("${POLL_SECONDS.first} to ${POLL_SECONDS.last}. How often the file on the screen is checked for a change made somewhere else.")),
        Key(
            "search", "skip_dirs", toml(SKIP_DIRS),
            listOf(
                "Folders, by name, that file search does not look in when the server cannot run git for a",
                "project. With git, the project's .gitignore decides instead. .git is never looked in.",
            ),
        ),
        Key("search", "max_files", "5000", listOf("${MAX_FILES.first} to ${MAX_FILES.last}. How many files that search collects before it stops.")),
    )

    /**
     * What a missing `settings.toml` is created with when the person first opens it: every key at its
     * default, commented out, with what it does. Commented out, so a default changed in a later
     * version reaches every key the person has not set themselves.
     */
    val TEMPLATE: String = buildString {
        append(
            """
            # Skiff Code settings. Saving this file applies it.
            # Each key is shown at its default with a # in front: take the # off and change the value to set it.
            # A key left out takes its default; a value that cannot be used is reported and replaced by it.
            # A key that a later version adds is put here, commented out, the next time this file is opened.
            """.trimIndent(),
        )
        append('\n')
        for ((table, keys) in KEYS.groupBy { it.table }) {
            append("\n[").append(table).append("]\n")
            for (key in keys) key.lines.forEach { append(it).append('\n') }
        }
    }

    /**
     * [text] with every key of [KEYS] it lacks added as [Key.lines], at the end of its own table — or
     * in that table added at the end of the file. A key counts as there when its line is, commented
     * out or not, so a key the person keeps commented out is left that way and nothing is added
     * twice. What is already there stays as it is, and a file that does not read as TOML is given
     * back untouched, since there is no telling where its tables are. A blank file is [TEMPLATE].
     */
    fun withMissingKeys(text: String): String {
        if (text.isBlank()) return TEMPLATE
        if (read(text).problems.any { it is SettingsProblem.Unreadable }) return text
        val lines = text.lines().toMutableList()
        var changed = false
        for ((table, keys) in KEYS.groupBy { it.table }) {
            val header = lines.indexOfFirst { tableHeader(table).matches(it) }
            if (header < 0) {
                while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
                lines += listOf("", "[$table]") + keys.flatMap { it.lines } + ""
                changed = true
                continue
            }
            var end = (header + 1..lines.lastIndex).firstOrNull { ANY_HEADER.matches(lines[it]) } ?: lines.size
            val missing = keys.filter { key -> (header + 1 until end).none { keyLine(key.name).matches(lines[it]) } }
            if (missing.isEmpty()) continue
            // Before the blank lines that part this table from the next, so they still do.
            while (end > header + 1 && lines[end - 1].isBlank()) end--
            lines.addAll(end, missing.flatMap { it.lines })
            changed = true
        }
        return if (changed) lines.joinToString("\n") else text
    }
}
