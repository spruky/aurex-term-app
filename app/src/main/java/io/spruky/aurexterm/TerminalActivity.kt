package io.spruky.aurexterm

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.spruky.aurexterm.databinding.ActivityTerminalBinding

/**
 * The embedded Debian terminal. Wires a [TermSession] (shell under proot) to a
 * scrolling output view and a single-line input, plus a toolbar of keys a soft
 * keyboard can't easily produce (Esc, Tab, arrows, Ctrl-prefix, ^C).
 *
 * Output is ANSI-scrubbed for the first pass; input from the text field is sent
 * line-by-line with a trailing newline. The toolbar sends raw control bytes.
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private lateinit var session: TermSession

    /** When true, the next typed character is sent as Ctrl+<char>. */
    private var ctrlArmed = false

    private val buffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = TermSession(
            ctx = this,
            onOutput = { chunk -> runOnUiThread { append(Ansi.strip(chunk)) } },
            onExit = { code -> runOnUiThread { append("\n[process exited: $code]\n") } },
        )

        wireInput()
        wireToolbar()
        session.start()
    }

    private fun append(text: String) {
        buffer.append(text)
        // Cap the scrollback so the TextView stays cheap.
        if (buffer.length > MAX_SCROLLBACK) {
            buffer.delete(0, buffer.length - MAX_SCROLLBACK)
        }
        binding.termOutput.text = buffer
        binding.termScroll.post { binding.termScroll.fullScroll(TextView.FOCUS_DOWN) }
    }

    private fun wireInput() {
        binding.termInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                val line = v.text.toString()
                session.send(line + "\n")
                v.text = ""
                true
            } else {
                false
            }
        }
    }

    private fun wireToolbar() {
        binding.kEsc.setOnClickListener { session.send("") }
        binding.kTab.setOnClickListener { session.send("\t") }
        binding.kUp.setOnClickListener { session.send("[A") }
        binding.kDown.setOnClickListener { session.send("[B") }
        binding.kRight.setOnClickListener { session.send("[C") }
        binding.kLeft.setOnClickListener { session.send("[D") }
        binding.kCtrlC.setOnClickListener { session.send("") }   // ETX
        binding.kClear.setOnClickListener {
            buffer.setLength(0)
            binding.termOutput.text = ""
            session.send("")   // FF / clear
        }
        binding.kCtrl.setOnClickListener {
            // Arm Ctrl: take the first char of the input field as Ctrl+<char>.
            val field = binding.termInput.text
            if (field.isNotEmpty()) {
                val c = field[0].uppercaseChar()
                val code = c.code - 'A'.code + 1
                if (code in 1..26) session.send(code.toChar().toString())
                binding.termInput.text?.delete(0, 1)
            } else {
                ctrlArmed = !ctrlArmed
            }
        }
    }

    override fun onDestroy() {
        session.stop()
        super.onDestroy()
    }

    private companion object {
        const val MAX_SCROLLBACK = 200_000
    }
}
