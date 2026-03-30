package com.gpxvideo.core.ui.component

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.gpxvideo.core.common.error.AppError
import com.gpxvideo.core.common.error.ErrorHandler

@Composable
fun ErrorSnackbarHost(
    errorHandler: ErrorHandler,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorHandler) {
        errorHandler.errors.collect { error ->
            val result = snackbarHostState.showSnackbar(
                message = error.message,
                actionLabel = if (onRetry != null) "Retry" else null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onRetry?.invoke()
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    )
}
