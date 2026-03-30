package com.gpxvideo.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.model.SportType
import com.gpxvideo.core.model.Template
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TemplateGalleryViewModel @Inject constructor(
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val _selectedSportFilter = MutableStateFlow<SportType?>(null)
    val selectedSportFilter: StateFlow<SportType?> = _selectedSportFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<Template>> = combine(
        _selectedSportFilter,
        _searchQuery
    ) { sport, query -> sport to query }
        .flatMapLatest { (sport, query) ->
            when {
                query.isNotBlank() -> templateRepository.searchTemplatesByName(query)
                sport != null -> templateRepository.getTemplatesBySport(sport)
                else -> templateRepository.getAllTemplates()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            templateRepository.initBuiltInTemplates()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSportFilter(sportType: SportType?) {
        _selectedSportFilter.value = sportType
    }

    fun deleteTemplate(templateId: UUID) {
        viewModelScope.launch {
            val template = templateRepository.getTemplateById(templateId)
            if (template != null && !template.isBuiltIn) {
                templateRepository.deleteTemplate(templateId)
            }
        }
    }
}
