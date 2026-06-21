package io.spruky.aurexterm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.spruky.aurexterm.databinding.ActivityKeyBinding

/**
 * Sign-in + key screen. The terminal key is minted server-side from the user's
 * Aurex email+password (issue_key) — the SAME credential axcode and the `term`
 * pip package expect. Users who already have a key (from those clients) can
 * paste it instead. The key shown here is copy-once, paste-everywhere.
 */
class KeyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeyBinding
    private var key: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSignIn.setOnClickListener { signIn() }
        binding.btnPaste.setOnClickListener { pasteKey() }
        binding.btnCopy.setOnClickListener { copyKey() }
        binding.btnVerify.setOnClickListener { verify() }
        binding.btnSignOut.setOnClickListener { signOut() }

        // Restore a previously issued/pasted key, if any.
        AurexKey.load(this)?.let { showKey(it, AurexKey.loadEmail(this)) }
    }

    // --- sign in: email+password -> server-issued key -----------------------

    private fun signIn() {
        val email = binding.inEmail.text.toString().trim()
        val password = binding.inPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter your email and password.")
            return
        }
        binding.btnSignIn.isEnabled = false
        Toast.makeText(this, "Signing in…", Toast.LENGTH_SHORT).show()

        Thread {
            val result = KeyClient.issueKey(email, password)
            runOnUiThread {
                binding.btnSignIn.isEnabled = true
                when (result) {
                    is KeyClient.IssueResult.Ok -> {
                        AurexKey.save(this, result.key, result.email)
                        binding.inPassword.text?.clear()
                        showKey(result.key, result.email)
                        toast("Signed in as ${result.email}")
                    }
                    is KeyClient.IssueResult.Error -> toast(result.message)
                }
            }
        }.start()
    }

    /** Accept a key the user already holds (from axcode / term). */
    private fun pasteKey() {
        val clip = clipboard().primaryClip
        val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
        if (AurexKey.isValid(pasted)) {
            AurexKey.save(this, pasted!!)
            showKey(pasted, null)
            toast("Key set — tap Verify to check it.")
        } else {
            toast("No valid aurex_term_ key on the clipboard. Copy it first.")
        }
    }

    // --- key readout actions ------------------------------------------------

    private fun copyKey() {
        val k = key ?: return
        clipboard().setPrimaryClip(ClipData.newPlainText("aurex_term_key", k))
        Toast.makeText(this, R.string.key_copied, Toast.LENGTH_SHORT).show()
    }

    private fun signOut() {
        AurexKey.clear(this)
        key = null
        binding.keyStatus.text = ""
        binding.keyBox.visibility = View.GONE
        binding.signInBox.visibility = View.VISIBLE
    }

    /** Hit the backend on a worker thread; report back on the UI thread. */
    private fun verify() {
        val k = key ?: return
        binding.btnVerify.isEnabled = false
        binding.keyStatus.setTextColor(getColor(R.color.text2))
        binding.keyStatus.text = "Verifying…"

        Thread {
            val result = KeyClient.verify(k)
            runOnUiThread {
                binding.btnVerify.isEnabled = true
                when (result) {
                    is KeyClient.Result.Ok ->
                        if (result.active) {
                            binding.keyStatus.setTextColor(getColor(R.color.green))
                            binding.keyStatus.text =
                                result.email?.takeIf { it.isNotBlank() }
                                    ?.let { "● Active — $it" }
                                    ?: "● Key is active"
                        } else {
                            binding.keyStatus.setTextColor(getColor(R.color.red2))
                            binding.keyStatus.text = "● Key not recognized"
                        }
                    is KeyClient.Result.Error -> {
                        binding.keyStatus.setTextColor(getColor(R.color.text2))
                        binding.keyStatus.text = "Couldn't verify: ${result.message}"
                    }
                }
            }
        }.start()
    }

    // --- ui helpers ---------------------------------------------------------

    private fun showKey(k: String, email: String?) {
        key = k
        binding.keyValue.text = k
        binding.signInBox.visibility = View.GONE
        binding.keyBox.visibility = View.VISIBLE
        if (email != null && email.isNotBlank()) {
            binding.keyStatus.setTextColor(getColor(R.color.text2))
            binding.keyStatus.text = email
        }
    }

    private fun clipboard() =
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
