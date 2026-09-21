package com.kmarko.nasdrive

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { CONNECT, BROWSE }

enum class SortOption { NAME, DATE_NEWEST, SIZE_LARGEST }

data class TransferProgress(
    val active: Boolean = false,
    val isUpload: Boolean = false,
    val fileName: String = "",
    val bytesDone: Long = 0L
)

data class MoveState(
    val entries: List<NasEntry>,
    val sourcePath: String
)

data class OpenFileRequest(
    val url: String,
    val mimeType: String
)

data class UiState(
    val screen: Screen = Screen.CONNECT,
    val savedConfig: SmbConfig? = null,
    val currentPath: String = "",
    val entries: List<NasEntry> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val transfer: TransferProgress = TransferProgress(),
    val pendingDelete: List<NasEntry> = emptyList(),
    val move: MoveState? = null,
    val openRequest: OpenFileRequest? = null,
    val creatingFolder: Boolean = false,
    val renameTarget: NasEntry? = null,
    val selectedNames: Set<String> = emptySet(),
    val sortOption: SortOption = SortOption.NAME
) {
    val isSelecting: Boolean get() = selectedNames.isNotEmpty()

    val displayEntries: List<NasEntry>
        get() {
            val byType = compareByDescending<NasEntry> { it.isDirectory }
            return when (sortOption) {
                SortOption.NAME -> entries.sortedWith(byType.thenBy { it.name.lowercase() })
                SortOption.DATE_NEWEST -> entries.sortedWith(byType.thenByDescending { it.lastModified })
                SortOption.SIZE_LARGEST -> entries.sortedWith(byType.thenByDescending { it.size })
            }
        }
}

class NasBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val configStore = ConfigStore(application)
    private var repository: SmbRepository? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        configStore.load()?.let { saved ->
            _uiState.value = _uiState.value.copy(savedConfig = saved)
        }
    }

    fun connect(config: SmbConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            try {
                repository?.close()
                val repo = SmbRepository(config)
                repo.testConnection()
                repository = repo
                configStore.save(config)
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    savedConfig = config,
                    screen = Screen.BROWSE,
                    currentPath = ""
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    errorMessage = "Connection failed: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            try {
                val entries = repo.list(_uiState.value.currentPath)
                _uiState.value = _uiState.value.copy(loading = false, entries = entries)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    errorMessage = "Failed to list files: ${e.message}"
                )
            }
        }
    }

    fun openFolder(name: String) {
        val repo = repository ?: return
        val current = _uiState.value.currentPath
        val newPath = if (current.isBlank()) name else "$current/$name"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            try {
                val entries = repo.list(newPath)
                // currentPath only advances on success, so a failed open leaves
                // navigation where it was instead of leaving a broken path that
                // the next tap would silently build on top of.
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    currentPath = newPath,
                    entries = entries,
                    selectedNames = emptySet()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    errorMessage = "Failed to list files: ${e.message}"
                )
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current.isBlank()) return
        val idx = current.lastIndexOf('/')
        val newPath = if (idx <= 0) "" else current.substring(0, idx)
        _uiState.value = _uiState.value.copy(currentPath = newPath, selectedNames = emptySet())
        refresh()
    }

    fun disconnect() {
        repository?.close()
        repository = null
        _uiState.value = UiState(savedConfig = _uiState.value.savedConfig)
    }

    fun downloadFile(entry: NasEntry, destUri: Uri, resolver: ContentResolver) {
        val repo = repository ?: return
        val current = _uiState.value.currentPath
        val remotePath = if (current.isBlank()) entry.name else "$current/${entry.name}"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                transfer = TransferProgress(active = true, isUpload = false, fileName = entry.name)
            )
            try {
                repo.download(remotePath, destUri, resolver) { bytes ->
                    _uiState.value = _uiState.value.copy(
                        transfer = _uiState.value.transfer.copy(bytesDone = bytes)
                    )
                }
                _uiState.value = _uiState.value.copy(
                    transfer = TransferProgress(),
                    statusMessage = "Downloaded ${entry.name}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transfer = TransferProgress(),
                    errorMessage = "Download failed: ${e.message}"
                )
            }
        }
    }

    fun uploadFile(localUri: Uri, fileName: String, resolver: ContentResolver) {
        val repo = repository ?: return
        val current = _uiState.value.currentPath
        val remotePath = if (current.isBlank()) fileName else "$current/$fileName"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                transfer = TransferProgress(active = true, isUpload = true, fileName = fileName)
            )
            try {
                repo.upload(localUri, remotePath, resolver) { bytes ->
                    _uiState.value = _uiState.value.copy(
                        transfer = _uiState.value.transfer.copy(bytesDone = bytes)
                    )
                }
                _uiState.value = _uiState.value.copy(
                    transfer = TransferProgress(),
                    statusMessage = "Uploaded $fileName"
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transfer = TransferProgress(),
                    errorMessage = "Upload failed: ${e.message}"
                )
            }
        }
    }

    fun openFile(entry: NasEntry) {
        if (entry.isDirectory) return
        val repo = repository ?: return
        val remotePath = fullPath(_uiState.value.currentPath, entry.name)
        viewModelScope.launch {
            try {
                val url = repo.streamUrl(remotePath)
                _uiState.value = _uiState.value.copy(
                    openRequest = OpenFileRequest(url, guessMimeType(entry.name))
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Open failed: ${e.message}")
            }
        }
    }

    fun clearOpenRequest() {
        _uiState.value = _uiState.value.copy(openRequest = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, statusMessage = null)
    }

    fun requestDelete(entry: NasEntry) {
        _uiState.value = _uiState.value.copy(pendingDelete = listOf(entry))
    }

    fun requestDeleteSelected() {
        val state = _uiState.value
        val entries = state.entries.filter { state.selectedNames.contains(it.name) }
        if (entries.isEmpty()) return
        _uiState.value = state.copy(pendingDelete = entries)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = emptyList())
    }

    fun confirmDelete() {
        val repo = repository ?: return
        val entries = _uiState.value.pendingDelete
        if (entries.isEmpty()) return
        val current = _uiState.value.currentPath

        if (entries.size == 1) {
            val entry = entries.first()
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(pendingDelete = emptyList(), loading = true, errorMessage = null)
                try {
                    repo.delete(fullPath(current, entry.name), entry.isDirectory)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        selectedNames = emptySet(),
                        statusMessage = "Deleted ${entry.name}"
                    )
                    refresh()
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        errorMessage = "Delete failed: ${e.message}"
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingDelete = emptyList(), loading = true, errorMessage = null)
            var failed = 0
            for (entry in entries) {
                try {
                    repo.delete(fullPath(current, entry.name), entry.isDirectory)
                } catch (e: Exception) {
                    failed++
                }
            }
            val succeeded = entries.size - failed
            _uiState.value = _uiState.value.copy(
                loading = false,
                selectedNames = emptySet(),
                statusMessage = if (failed == 0) "Deleted $succeeded items" else null,
                errorMessage = if (failed > 0) "Deleted $succeeded/${entries.size} items, $failed failed" else null
            )
            refresh()
        }
    }

    fun startMove(entry: NasEntry) {
        _uiState.value = _uiState.value.copy(move = MoveState(listOf(entry), _uiState.value.currentPath))
    }

    fun startMoveSelected() {
        val state = _uiState.value
        val entries = state.entries.filter { state.selectedNames.contains(it.name) }
        if (entries.isEmpty()) return
        _uiState.value = state.copy(move = MoveState(entries, state.currentPath), selectedNames = emptySet())
    }

    fun cancelMove() {
        _uiState.value = _uiState.value.copy(move = null)
    }

    fun confirmMoveHere() {
        val repo = repository ?: return
        val move = _uiState.value.move ?: return
        val current = _uiState.value.currentPath

        if (move.entries.size == 1) {
            val entry = move.entries.first()
            val sourcePath = fullPath(move.sourcePath, entry.name)
            val destPath = fullPath(current, entry.name)
            if (sourcePath == destPath) {
                _uiState.value = _uiState.value.copy(move = null, errorMessage = "Already in this folder")
                return
            }
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
                try {
                    repo.move(sourcePath, destPath, entry.isDirectory)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        move = null,
                        statusMessage = "Moved ${entry.name}"
                    )
                    refresh()
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        move = null,
                        errorMessage = "Move failed: ${e.message}"
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            var moved = 0
            var skipped = 0
            var failed = 0
            for (entry in move.entries) {
                val sourcePath = fullPath(move.sourcePath, entry.name)
                val destPath = fullPath(current, entry.name)
                if (sourcePath == destPath) {
                    skipped++
                    continue
                }
                try {
                    repo.move(sourcePath, destPath, entry.isDirectory)
                    moved++
                } catch (e: Exception) {
                    failed++
                }
            }
            val message = buildString {
                append("Moved $moved item")
                if (moved != 1) append("s")
                if (skipped > 0) append(", $skipped already here")
                if (failed > 0) append(", $failed failed")
            }
            _uiState.value = _uiState.value.copy(
                loading = false,
                move = null,
                statusMessage = if (failed == 0) message else null,
                errorMessage = if (failed > 0) message else null
            )
            refresh()
        }
    }

    fun setSortOption(option: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
    }

    fun toggleSelection(entry: NasEntry) {
        val current = _uiState.value.selectedNames
        val updated = if (current.contains(entry.name)) current - entry.name else current + entry.name
        _uiState.value = _uiState.value.copy(selectedNames = updated)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedNames = emptySet())
    }

    fun requestCreateFolder() {
        _uiState.value = _uiState.value.copy(creatingFolder = true)
    }

    fun cancelCreateFolder() {
        _uiState.value = _uiState.value.copy(creatingFolder = false)
    }

    fun confirmCreateFolder(name: String) {
        val repo = repository ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(creatingFolder = false)
            return
        }
        val path = fullPath(_uiState.value.currentPath, trimmed)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(creatingFolder = false, loading = true, errorMessage = null)
            try {
                repo.createFolder(path)
                _uiState.value = _uiState.value.copy(loading = false, statusMessage = "Created $trimmed")
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    errorMessage = "Create folder failed: ${e.message}"
                )
            }
        }
    }

    fun requestRename(entry: NasEntry) {
        _uiState.value = _uiState.value.copy(renameTarget = entry)
    }

    fun cancelRename() {
        _uiState.value = _uiState.value.copy(renameTarget = null)
    }

    fun confirmRename(newName: String) {
        val repo = repository ?: return
        val entry = _uiState.value.renameTarget ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == entry.name) {
            _uiState.value = _uiState.value.copy(renameTarget = null)
            return
        }
        val current = _uiState.value.currentPath
        val sourcePath = fullPath(current, entry.name)
        val destPath = fullPath(current, trimmed)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(renameTarget = null, loading = true, errorMessage = null)
            try {
                repo.move(sourcePath, destPath, entry.isDirectory)
                _uiState.value = _uiState.value.copy(loading = false, statusMessage = "Renamed to $trimmed")
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    errorMessage = "Rename failed: ${e.message}"
                )
            }
        }
    }

    private fun fullPath(path: String, name: String) = if (path.isBlank()) name else "$path/$name"

    override fun onCleared() {
        repository?.close()
        super.onCleared()
    }
}
