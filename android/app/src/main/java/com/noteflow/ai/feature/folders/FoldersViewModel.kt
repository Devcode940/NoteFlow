package com.noteflow.ai.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.Folder
import com.noteflow.ai.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<FoldersUiState> = combine(
        folderRepository.observeFolders()
    ) { folders ->
        FoldersUiState(folders = folders, message = _message.value)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun createFolder(name: String, icon: String, colorHex: String) {
        viewModelScope.launch {
            when (val result = folderRepository.createFolder(name, icon, colorHex)) {
                is Result.Success -> _message.value = "Folder created"
                is Result.Failure -> _message.value = result.message
            }
        }
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            when (val result = folderRepository.updateFolder(folder)) {
                is Result.Success -> _message.value = "Folder updated"
                is Result.Failure -> _message.value = result.message
            }
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            when (val result = folderRepository.deleteFolder(id)) {
                is Result.Success -> _message.value = "Folder deleted (notes unfiled)"
                is Result.Failure -> _message.value = result.message
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val message: String? = null
)
