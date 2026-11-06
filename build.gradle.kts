// Plugins are declared once here (not applied) so every module resolves the same AGP/KGP from a
// single classloader; the convention plugins in build-logic apply them.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
