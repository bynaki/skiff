package com.naki.skiff.code.settings

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/**
 * What `settings.toml` holds: what the person chose, as opposed to what the page noticed as it was
 * used, which stays in its `localStorage` (docs/skiffcode.spec.md "상태 저장"). Only the keys that
 * something reads today are here; the diff's transparency and the language servers join with the
 * features that read them. A key left out of the file takes the default written here, and
 * [TEMPLATE] says the same thing in the file a person opens — `SettingsTomlTest` holds the two to it.
 */
@Serializable
data class Settings(
    val editor: Editor = Editor(),
    val files: Files = Files(),
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
            Toml.decodeFromString(Settings.serializer(), text)
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
        val font = if (editor.font.isNotBlank()) editor.font else {
            problems += SettingsProblem.Refused("editor.font", "\"${editor.font}\"", "\"${defaults.editor.font}\"", null)
            defaults.editor.font
        }
        val theme = if (editor.theme in themes) editor.theme else {
            problems += SettingsProblem.Refused("editor.theme", "\"${editor.theme}\"", "\"${defaults.editor.theme}\"", null)
            defaults.editor.theme
        }
        val settings = Settings(
            editor = Settings.Editor(
                font = font,
                fontSize = inRange("editor.font_size", editor.fontSize, FONT_SIZES, defaults.editor.fontSize),
                tabSize = inRange("editor.tab_size", editor.tabSize, TAB_SIZES, defaults.editor.tabSize),
                wrap = editor.wrap,
                theme = theme,
            ),
            files = Settings.Files(
                maxSizeMb = inRange("files.max_size_mb", files.maxSizeMb, MAX_SIZES_MB, defaults.files.maxSizeMb),
                keptBuffers = inRange("files.kept_buffers", files.keptBuffers, KEPT_BUFFERS, defaults.files.keptBuffers),
                pollSeconds = inRange("files.poll_seconds", files.pollSeconds, POLL_SECONDS, defaults.files.pollSeconds),
            ),
        )
        return SettingsRead(settings, problems)
    }

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

    private val UNKNOWN_KEY = Regex("Unknown key received: <([^>]*)> in scope <([^>]*)>")

    /**
     * [text] with `[editor]`'s `theme` set to [name], for the palette's Theme commands. Only that
     * line changes, so the person's comments and the rest of their values stay as they wrote them;
     * a file without the line gets it at the top of `[editor]`, and one without that table gets both
     * at the end.
     */
    fun withTheme(text: String, name: String): String {
        val line = "theme = \"$name\""
        val lines = text.lines().toMutableList()
        val header = lines.indexOfFirst { EDITOR_HEADER.matches(it) }
        if (header < 0) return text.trimEnd('\n').let { if (it.isEmpty()) "" else "$it\n\n" } + "[editor]\n$line\n"
        val end = (header + 1..lines.lastIndex).firstOrNull { ANY_HEADER.matches(lines[it]) } ?: lines.size
        val existing = (header + 1 until end).firstOrNull { THEME_LINE.matches(lines[it]) }
        if (existing != null) lines[existing] = line else lines.add(header + 1, line)
        return lines.joinToString("\n")
    }

    private val EDITOR_HEADER = Regex("""\s*\[\s*editor\s*]\s*(#.*)?""")
    private val ANY_HEADER = Regex("""\s*\[.*""")
    private val THEME_LINE = Regex("""\s*theme\s*=.*""")

    /**
     * What a missing `settings.toml` is created with when the person first opens it: every key, at
     * its default, with what it does. Written by hand rather than encoded so it can say so.
     */
    val TEMPLATE = """
        # Skiff Code settings. Saving this file applies it.
        # A key left out takes its default; a value that cannot be used is reported and replaced by it.

        [editor]
        # A CSS font family. A font the device does not have falls back to the next one listed.
        font = "monospace"
        # 8 to 40. What Reset Zoom returns to; a pinch changes only what you are looking at now.
        font_size = 14
        # 1 to 16. How wide a tab character is drawn.
        tab_size = 4
        # Wrap long lines at the edge of the screen instead of scrolling sideways.
        wrap = true
        # "system" follows the device's dark mode; "light" and "dark" stay put. A theme brought in
        # with Import Theme goes by its file's name. The palette's Theme commands write this line.
        theme = "system"

        [files]
        # 1 to 64. A larger file is not opened.
        max_size_mb = 2
        # 0 to 200. How many files off the screen keep their undo history.
        kept_buffers = 30
        # 1 to 60. How often the file on the screen is checked for a change made somewhere else.
        poll_seconds = 2
    """.trimIndent() + "\n"
}
