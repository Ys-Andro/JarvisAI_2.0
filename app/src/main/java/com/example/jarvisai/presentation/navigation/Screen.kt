package com.example.jarvisai.presentation.navigation

sealed class Screen(val route: String) {
    object Chat : Screen("chat_screen")
    object Library : Screen("library_screen")
    object Models : Screen("models_screen")
    object Settings : Screen("settings_screen")
}
