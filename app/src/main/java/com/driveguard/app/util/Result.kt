package com.driveguard.app.util

/** Lightweight result type for user-facing operations. */
sealed interface UiResult<out T> {
    data class Success<T>(val value: T) : UiResult<T>
    data class Failure(val message: String?) : UiResult<Nothing>
    data object Loading : UiResult<Nothing>
}
