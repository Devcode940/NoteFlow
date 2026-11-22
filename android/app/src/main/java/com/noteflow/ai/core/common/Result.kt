package com.noteflow.ai.core.common

/**
 * Explicit result type used across repository boundaries so failures are
 * handled by callers instead of being swallowed or thrown across layers.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>

    data class Failure<T>(val message: String, val cause: Throwable? = null) : Result<T>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    fun exceptionOrNull(): Throwable? = (this as? Failure)?.cause

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> Failure(message, cause)
    }
}

fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Failure -> cause ?: IllegalStateException(message)
}
