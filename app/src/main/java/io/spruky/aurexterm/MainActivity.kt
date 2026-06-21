package io.spruky.aurexterm

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.spruky.aurexterm.databinding.ActivityMainBinding

/**
 * Launcher. Two tiles — Terminal (embedded Debian) and Key (the shared
 * aurex_term_<32hex> credential). Nothing here talks to the network; both
 * tiles just route to their respective activities.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileTerminal.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        binding.tileKey.setOnClickListener {
            startActivity(Intent(this, KeyActivity::class.java))
        }
    }
}
