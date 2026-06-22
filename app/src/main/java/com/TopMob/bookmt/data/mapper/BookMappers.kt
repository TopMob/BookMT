package com.TopMob.bookmt.data.mapper

import com.TopMob.bookmt.data.local.entity.BookEntity
import com.TopMob.bookmt.data.local.entity.BookmarkEntity
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookmarkType

/* ---- Book <-> BookEntity ---- */

fun BookEntity.toDomain(): Book = Book(
    id = id,
    uri = uri,
    title = title,
    author = author,
    format = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.UNKNOWN),
    coverImagePath = coverImagePath,
    progress = progressPercent,
    lastReadTimestamp = lastReadTimestamp,
    addedTimestamp = addedTimestamp,
    tags = tags,
    lastReadPosition = lastReadPosition,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    uri = uri,
    title = title,
    author = author,
    format = format.name,
    coverImagePath = coverImagePath,
    progressPercent = progress,
    lastReadPosition = lastReadPosition,
    lastReadTimestamp = lastReadTimestamp,
    addedTimestamp = addedTimestamp,
    tags = tags,
)

/* ---- Bookmark <-> BookmarkEntity ---- */

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    bookId = bookId,
    type = runCatching { BookmarkType.valueOf(type) }.getOrDefault(BookmarkType.BOOKMARK),
    position = position,
    endPosition = endPosition,
    textSnippet = textSnippet,
    note = note,
    colorArgb = colorArgb,
    chapterTitle = chapterTitle,
    timestamp = timestamp,
)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    bookId = bookId,
    type = type.name,
    position = position,
    endPosition = endPosition,
    textSnippet = textSnippet,
    note = note,
    colorArgb = colorArgb,
    chapterTitle = chapterTitle,
    timestamp = timestamp,
)
