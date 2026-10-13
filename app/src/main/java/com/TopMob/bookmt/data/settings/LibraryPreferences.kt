package com.TopMob.bookmt.data.settings

import android.content.Context
import com.TopMob.bookmt.domain.model.BookQuery
import com.TopMob.bookmt.domain.model.BookSortOrder
import com.TopMob.bookmt.presentation.bookshelf.BookshelfViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("library_view", Context.MODE_PRIVATE)
    fun query() = BookQuery(
        sortOrder = BookSortOrder.entries.firstOrNull { it.name == prefs.getString("sort", null) } ?: BookSortOrder.RECENTLY_ADDED,
        ascending = prefs.getBoolean("ascending", false),
    )
    fun viewMode() = BookshelfViewMode.entries.firstOrNull { it.name == prefs.getString("view", null) } ?: BookshelfViewMode.GRID
    fun saveQuery(query: BookQuery) { prefs.edit().putString("sort", query.sortOrder.name).putBoolean("ascending", query.ascending).apply() }
    fun saveViewMode(mode: BookshelfViewMode) { prefs.edit().putString("view", mode.name).apply() }
}
