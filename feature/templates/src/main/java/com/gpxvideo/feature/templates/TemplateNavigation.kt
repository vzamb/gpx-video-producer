package com.gpxvideo.feature.templates

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
object TemplateGalleryRoute

@Serializable
data class TemplateEditorRoute(val templateId: String? = null)

fun NavGraphBuilder.templateGalleryScreen(
    onTemplateSelected: (UUID) -> Unit,
    onCreateTemplate: () -> Unit,
    onNavigateBack: () -> Unit
) {
    composable<TemplateGalleryRoute> {
        TemplateGalleryScreen(
            onTemplateSelected = onTemplateSelected,
            onCreateTemplate = onCreateTemplate,
            onNavigateBack = onNavigateBack
        )
    }
}

fun NavGraphBuilder.templateEditorScreen(
    onSave: () -> Unit,
    onNavigateBack: () -> Unit
) {
    composable<TemplateEditorRoute> { backStackEntry ->
        val templateId = backStackEntry.arguments?.getString("templateId")
        TemplateEditorScreen(
            templateId = templateId,
            onSave = onSave,
            onNavigateBack = onNavigateBack
        )
    }
}
