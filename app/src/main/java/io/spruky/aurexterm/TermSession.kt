package io.spruky.aurexterm

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * Owns the lifecycle of one embedded-Debian shell.
 *
 * Copies the proot helper scripts and the native proot binary out to the app's
 * private storage, runs bootstrap (first launch only), then spawns the login
 * shell under proot. Output is streamed to [onOutput]; write to the shell with
 * [send]. Everything off the main thread — callers post UI updates themselves.
 *
 * This is a process/pipe pipeline, not a real PTY. It's enough to run an
 * interactive shell for a first pass; a JNI PTY can replace [start] later
 * without touching the Activity.
 */
class TermSession(
    private val ctx: Context,
    private val onOutput: (String) -> Unit,
    private val onExit: (Int) -> Unit,
) {
    private val prefix: File = ctx.filesDir
    private val rootfs = File(prefix, "debian")
    private val tmpDir = File(prefix, "tmp").apply { mkdirs() }

    @Volatile private var process: Process? = null
    @Volatile private var writer: OutputStreamWriter? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({ run() }, "aurex-term-session").start()
    }

    private fun run() {
        try {
            val proot = locateProot()
            if (proot == null) {
                onOutput("\r\nproot binary not found. This APK was built without the native payload.\r\n")
                onExit(127)
                return
            }

            val bootstrap = copyAsset("proot/bootstrap.sh")
            val launch = copyAsset("proot/launch.sh")
            val tarball = locateRootfsTarball()

            val env = arrayOf(
                "PREFIX=${prefix.absolutePath}",
                "ROOTFS=${rootfs.absolutePath}",
                "TMPDIR=${tmpDir.absolutePath}",
                "PROOT=${proot.absolutePath}",
                "PROOT_LOADER=${loaderPath()}",
                "TARBALL=${tarball?.absolutePath ?: ""}",
                "MARKER=${File(rootfs, ".installed").absolutePath}",
                "TERM=xterm-256color",
            )

            // Bootstrap (idempotent — the script no-ops once the marker exists).
            if (tarball == null && !File(rootfs, ".installed").exists()) {
                onOutput("\r\nNo Debian rootfs bundled. CI fetches it into assets at build time.\r\n")
                onExit(1)
                return
            }
            onOutput("[2mPreparing Debian…[0m\r\n")
            val boot = ProcessBuilder("/system/bin/sh", bootstrap.absolutePath)
                .redirectErrorStream(true)
                .also { it.environment().putAll(env.toMap()) }
                .start()
            pump(boot)
            boot.waitFor()

            // Launch the interactive shell.
            val p = ProcessBuilder("/system/bin/sh", launch.absolutePath)
                .redirectErrorStream(true)
                .also { it.environment().putAll(env.toMap()) }
                .start()
            process = p
            writer = OutputStreamWriter(p.outputStream, Charsets.UTF_8)

            pump(p)
            val code = p.waitFor()
            onExit(code)
        } catch (e: Exception) {
            onOutput("\r\nsession error: ${e.message}\r\n")
            onExit(1)
        } finally {
            running = false
        }
    }

    private fun pump(p: Process) {
        val buf = CharArray(4096)
        val reader = p.inputStream.reader(Charsets.UTF_8)
        while (true) {
            val n = try {
                reader.read(buf)
            } catch (_: IOException) {
                break
            }
            if (n < 0) break
            if (n > 0) onOutput(String(buf, 0, n))
        }
    }

    /** Send a line (or raw bytes) to the shell's stdin. */
    fun send(text: String) {
        val w = writer ?: return
        Thread {
            try {
                w.write(text)
                w.flush()
            } catch (_: IOException) {
            }
        }.start()
    }

    fun stop() {
        running = false
        try {
            writer?.close()
        } catch (_: IOException) {
        }
        process?.destroy()
        process = null
    }

    // --- asset / binary plumbing --------------------------------------------

    private fun copyAsset(name: String): File {
        val out = File(prefix, name.substringAfterLast('/'))
        ctx.assets.open(name).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.setExecutable(true, false)
        return out
    }

    /**
     * proot ships as a native lib so the installer extracts and marks it
     * executable for us. CI names it libproot.so in jniLibs. Fall back to a
     * copy in assets for builds that bundle it there instead.
     */
    private fun locateProot(): File? {
        val fromJni = File(ctx.applicationInfo.nativeLibraryDir, "libproot.so")
        if (fromJni.exists()) return fromJni
        return try {
            copyAsset("proot/proot").takeIf { it.length() > 0 }
        } catch (_: IOException) {
            null
        }
    }

    private fun loaderPath(): String {
        val loader = File(ctx.applicationInfo.nativeLibraryDir, "libloader.so")
        return if (loader.exists()) loader.absolutePath else File(prefix, "libloader.so").absolutePath
    }

    /** CI drops the rootfs tarball at assets/bootstrap/rootfs.tar.* . */
    private fun locateRootfsTarball(): File? {
        return try {
            val names = ctx.assets.list("bootstrap") ?: return null
            val tar = names.firstOrNull { it.startsWith("rootfs.tar") } ?: return null
            copyAsset("bootstrap/$tar")
        } catch (_: IOException) {
            null
        }
    }

    private fun Array<String>.toMap(): Map<String, String> =
        associate { it.substringBefore('=') to it.substringAfter('=') }
}
