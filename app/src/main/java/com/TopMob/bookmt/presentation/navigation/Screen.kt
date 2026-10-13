package com.TopMob.bookmt.presentation.navigation

/** Type-safe route definitions for the app's navigation graph. */
sealed class Screen(val route: String) {

    data object Bookshelf : Screen("bookshelf")

    data object Settings : Screen("settings")
    data object Bookmarks : Screen("bookmarks")

    data object BookDetails : Screen("book/{$ARG_BOOK_ID}") {
        fun createRoute(bookId: Long): String = "book/$bookId"
    }

    data object Reader : Screen("reader/{$ARG_BOOK_ID}?offset={$ARG_OFFSET}") {
        fun createRoute(bookId: Long, offset: Int = -1): String = "reader/$bookId?offset=$offset"
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        const val ARG_OFFSET = "offset"
    }
}
