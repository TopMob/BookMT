package com.TopMob.bookmt.core.common

/**
 * A discriminated union representing the outcome of an operation that can fail — used for one-shot
 * domain operations (import a book, parse content) where we need to model loading/success/error
 * without throwing across layer boundaries.
 */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val throwable: Throwable, val message: String? = throwable.message) :
        Resource<Nothing>
}

inline fun <T, R> Resource<T>.map(transform: (T) -> R): Resource<R> = when (this) {
    is Resource.Success -> Resource.Success(transform(data))
    is Resource.Error -> this
    Resource.Loading -> Resource.Loading
}

inline fun <T> Resource<T>.onSuccess(action: (T) -> Unit): Resource<T> {
    if (this is Resource.Success) action(data)
    return this
}

inline fun <T> Resource<T>.onError(action: (Throwable) -> Unit): Resource<T> {
    if (this is Resource.Error) action(throwable)
    return this
}

/** Runs [block], wrapping its result in a [Resource], converting any thrown exception to [Resource.Error]. */
inline fun <T> resourceCatching(block: () -> T): Resource<T> = try {
    Resource.Success(block())
} catch (t: Throwable) {
    Resource.Error(t)
}
