package com.gpxvideo.core.common.error

sealed class AppError(val message: String, val cause: Throwable? = null) {
    class MediaImportError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class GpxParseError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class ExportError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class StorageError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class DatabaseError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class UnknownError(message: String, cause: Throwable? = null) : AppError(message, cause)
}
