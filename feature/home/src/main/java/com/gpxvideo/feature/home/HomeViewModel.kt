package com.gpxvideo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.entity.ProjectEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectDao: ProjectDao
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                projectDao.getAll()
            } else {
                projectDao.searchByName(query)
            }
        }
        .combine(searchQuery) { projects, query ->
            HomeUiState(
                projects = projects,
                searchQuery = query,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun deleteProject(projectId: UUID) {
        viewModelScope.launch {
            projectDao.deleteById(projectId)
        }
    }
}
