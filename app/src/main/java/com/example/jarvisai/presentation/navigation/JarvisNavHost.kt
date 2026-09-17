package com.example.jarvisai.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jarvisai.presentation.chat.ChatScreen
import com.example.jarvisai.presentation.chat.ChatViewModel
import com.example.jarvisai.presentation.library.LibraryScreen
import com.example.jarvisai.presentation.library.LibraryViewModel
import com.example.jarvisai.presentation.models.ModelsScreen
import com.example.jarvisai.presentation.models.ModelsViewModel
import com.example.jarvisai.presentation.settings.SettingsScreen

@Composable
fun JarvisNavHost(
    chatViewModel: ChatViewModel,
    libraryViewModel: LibraryViewModel,
    modelsViewModel: ModelsViewModel,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() },
        modifier = modifier
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToModels = {
                    navController.navigate(Screen.Models.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.Library.route)
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onConversationSelected = { conversationId ->
                    chatViewModel.selectConversation(conversationId)
                    navController.popBackStack(Screen.Chat.route, inclusive = false)
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Models.route) {
            ModelsScreen(
                viewModel = modelsViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = modelsViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
