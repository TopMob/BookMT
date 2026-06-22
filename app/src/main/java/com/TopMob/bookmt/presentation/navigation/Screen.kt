package com.TopMob.bookmt.presentation.navigation

/** Type-safe route definitions for the app's navigation graph. */
sealed class Screen(val route: String) {

    data object Bookshelf : Screen("bookshelf")

    data object Settings : Screen("settings")

    data object BookDetails : Screen("book/{$ARG_BOOK_ID}") {
        fun createRoute(bookId: Long): String = "book/$bookId"
    }

    data object Reader : Screen("reader/{$ARG_BOOK_ID}") {
        fun createRoute(bookId: Long): String = "reader/$bookId"
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
    }
}
