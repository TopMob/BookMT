package com.TopMob.bookmt.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.TopMob.bookmt.presentation.bookshelf.BookDetailsScreen
import com.TopMob.bookmt.presentation.bookshelf.BookshelfScreen
import com.TopMob.bookmt.presentation.reader.ReaderScreen
import com.TopMob.bookmt.presentation.settings.AppSettingsScreen

/**
 * Root navigation graph. Bookshelf is the start destination; tapping a book navigates to the reader
 * with the book id as a typed nav argument.
 */
@Composable
fun BookMTNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Bookshelf.route,
    ) {
        composable(Screen.Bookshelf.route) {
            BookshelfScreen(
                onBookClick = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId))
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onOpenDetails = { bookId ->
                    navController.navigate(Screen.BookDetails.createRoute(bookId))
                },
            )
        }

        composable(Screen.Settings.route) {
            AppSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.BookDetails.route,
            arguments = listOf(
                navArgument(Screen.ARG_BOOK_ID) { type = NavType.LongType },
            ),
        ) {
            BookDetailsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument(Screen.ARG_BOOK_ID) { type = NavType.LongType },
            ),
        ) {
            ReaderScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
