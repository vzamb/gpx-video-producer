package com.gpxvideo.core.common.error

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorHandler @Inject constructor() {

    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 10)
    val errors: SharedFlow<AppError> = _errors

    fun handleError(error: AppError) {
        Log.e(TAG, "App error: ${error.message}", error.cause)
        _errors.tryEmit(error)
    }

    fun handleException(throwable: Throwable, context: String = "") {
        val message = if (context.isNotBlank()) {
            "$context: ${throwable.localizedMessage ?: "Unknown error"}"
        } else {
            throwable.localizedMessage ?: "Unknown error"
        }
        Log.e(TAG, message, throwable)

        val error = when (throwable) {
            is java.io.IOException -> AppError.StorageError(message, throwable)
            is IllegalArgumentException -> AppError.MediaImportError(message, throwable)
            else -> AppError.UnknownError(message, throwable)
        }
        _errors.tryEmit(error)
    }

    companion object {
        private const val TAG = "ErrorHandler"
    }
}
