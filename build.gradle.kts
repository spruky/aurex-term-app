// Top-level build file. Plugins are declared here with `apply false` so each
// module opts in; versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
