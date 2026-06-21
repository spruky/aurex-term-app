package io.spruky.aurexterm

/**
 * Minimal ANSI scrubber. The first-pass terminal renders plain text, so we drop
 * the escape sequences a shell emits (colours, cursor moves, title sets) rather
 * than interpret them. A real terminal-emulator view can replace this later.
 */
object Ansi {

    private const val ESC = ""

    // CSI: ESC [ ... <final byte>   (colours, cursor movement, erases)
    private val CSI = Regex("$ESC\\[[0-9;?]*[ -/]*[@-~]")
    // OSC: ESC ] ... (BEL | ESC \)  (window/icon title)
    private val OSC = Regex("$ESC\\][^$ESC]*(?:|$ESC\\\\)")
    // Charset selects, keypad modes, and other 2-char escapes.
    private val MISC = Regex("$ESC[()][0-9A-Za-z]|$ESC[=>]|$ESC[78]")

    fun strip(s: String): String =
        s.replace(CSI, "")
            .replace(OSC, "")
            .replace(MISC, "")
            .replace("", "")   // BEL
            .replace("", "")   // any stray ESC
}
