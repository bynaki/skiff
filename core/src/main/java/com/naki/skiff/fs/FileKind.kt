package com.naki.skiff.fs

/**
 * Coarse file classification. Today it only drives the list icon and which external
 * app we offer; the preview viewer will branch on the same values once it lands.
 */
enum class FileKind {
    DIRECTORY, MARKDOWN, CODE, TEXT, IMAGE, VIDEO, AUDIO, ARCHIVE, PDF, OTHER;

    companion object {
        private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")
        private val CODE_EXTENSIONS = setOf(
            "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx", "c", "h", "cc",
            "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift", "scala", "sh", "bash", "zsh",
            "fish", "sql", "html", "htm", "css", "scss", "xml", "json", "yaml", "yml", "toml",
            "ini", "cfg", "conf", "gradle", "properties", "dockerfile", "makefile", "lua", "vim",
        )
        private val TEXT_EXTENSIONS = setOf("txt", "log", "csv", "tsv", "rst", "adoc", "tex")
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "avif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "bz2", "xz", "7z", "rar", "tgz", "zst")

        fun of(node: FileNode): FileKind = if (node.navigable) DIRECTORY else of(node.name)

        fun of(fileName: String): FileKind {
            val extension = FsPath.extension(fileName)
            // Extension-less build files are still code.
            if (extension.isEmpty() && fileName.lowercase() in CODE_EXTENSIONS) return CODE
            return when (extension) {
                in MARKDOWN_EXTENSIONS -> MARKDOWN
                in CODE_EXTENSIONS -> CODE
                in TEXT_EXTENSIONS -> TEXT
                in IMAGE_EXTENSIONS -> IMAGE
                in VIDEO_EXTENSIONS -> VIDEO
                in AUDIO_EXTENSIONS -> AUDIO
                in ARCHIVE_EXTENSIONS -> ARCHIVE
                "pdf" -> PDF
                else -> OTHER
            }
        }
    }
}
