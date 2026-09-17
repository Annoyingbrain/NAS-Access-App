package com.kmarko.nasdrive

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kmarko.nasdrive.ui.BrowserScreen
import com.kmarko.nasdrive.ui.ConnectionScreen
import com.kmarko.nasdrive.ui.theme.NasDriveTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NasBrowserViewModel by viewModels()

    private var pendingDownloadEntry: NasEntry? = null

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryFileName(contentResolver, uri) ?: "upload_${System.currentTimeMillis()}"
            viewModel.uploadFile(uri, name, contentResolver)
        }
    }

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val entry = pendingDownloadEntry
        pendingDownloadEntry = null
        if (uri != null && entry != null) {
            viewModel.downloadFile(entry, uri, contentResolver)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NasDriveTheme {
                val state by viewModel.uiState.collectAsState()
                when (state.screen) {
                    Screen.CONNECT -> ConnectionScreen(
                        savedConfig = state.savedConfig,
                        loading = state.loading,
                        errorMessage = state.errorMessage,
                        onConnect = { viewModel.connect(it) }
                    )
                    Screen.BROWSE -> BrowserScreen(
                        state = state,
                        onOpenFolder = { viewModel.openFolder(it) },
                        onNavigateUp = { viewModel.navigateUp() },
                        onRefresh = { viewModel.refresh() },
                        onDisconnect = { viewModel.disconnect() },
                        onDownload = { entry ->
                            pendingDownloadEntry = entry
                            createDocumentLauncher.launch(entry.name)
                        },
                        onUpload = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        onRequestDelete = { viewModel.requestDelete(it) },
                        onCancelDelete = { viewModel.cancelDelete() },
                        onConfirmDelete = { viewModel.confirmDelete() },
                        onStartMove = { viewModel.startMove(it) },
                        onCancelMove = { viewModel.cancelMove() },
                        onConfirmMove = { viewModel.confirmMoveHere() },
                        onDismissMessage = { viewModel.clearMessages() }
                    )
                }
            }
        }
    }

    private fun queryFileName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return null
    }
}
