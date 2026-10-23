package com.TopMob.bookmt.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.presentation.theme.LocalComfortMotion
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.TopMob.bookmt.presentation.bookshelf.BookDetailsScreen
import com.TopMob.bookmt.presentation.bookshelf.BookshelfScreen
import com.TopMob.bookmt.presentation.bookshelf.GlobalBookmarksScreen
import com.TopMob.bookmt.presentation.reader.ReaderScreen
import com.TopMob.bookmt.presentation.settings.AppSettingsScreen

/**
 * Root navigation graph. Bookshelf is the start destination; tapping a book navigates to the reader
 * with the book id as a typed nav argument.
 */
@Composable
fun BookMTNavHost(
    navController: NavHostController = rememberNavController(),
    importedBookId: Long? = null,
    onImportHandled: () -> Unit = {},
) {
    androidx.compose.runtime.LaunchedEffect(importedBookId) {
        importedBookId?.let {
            navController.navigate(Screen.Reader.createRoute(it)) { launchSingleTop = true }
            onImportHandled()
        }
    }
    val motion = LocalComfortMotion.current
    val distance = with(LocalDensity.current) { 12.dp.roundToPx() }
    NavHost(
        navController = navController,
        startDestination = Screen.Bookshelf.route,
        enterTransition = {
            if (initialState.destination.route == Screen.Reader.route || targetState.destination.route == Screen.Reader.route)
                EnterTransition.None
            else fadeIn(tween(motion.duration(160))) + if (motion.spatial)
                slideInHorizontally(tween(160)) { distance } else EnterTransition.None
        },
        exitTransition = {
            if (initialState.destination.route == Screen.Reader.route || targetState.destination.route == Screen.Reader.route)
                ExitTransition.None else fadeOut(tween(motion.duration(120)))
        },
        popEnterTransition = {
            if (initialState.destination.route == Screen.Reader.route || targetState.destination.route == Screen.Reader.route)
                EnterTransition.None else fadeIn(tween(motion.duration(120)))
        },
        popExitTransition = {
            if (initialState.destination.route == Screen.Reader.route || targetState.destination.route == Screen.Reader.route)
                ExitTransition.None else fadeOut(tween(motion.duration(100)))
        },
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
                onOpenBookmarks = { navController.navigate(Screen.Bookmarks.route) },
            )
        }

        composable(Screen.Settings.route) {
            AppSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Bookmarks.route) {
            GlobalBookmarksScreen(
                onBack = { navController.popBackStack() },
                onOpen = { bookmark -> navController.navigate(Screen.Reader.createRoute(bookmark.bookId, bookmark.position)) },
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
                navArgument(Screen.ARG_OFFSET) { type = NavType.IntType; defaultValue = -1 },
            ),
        ) {
            ReaderScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
