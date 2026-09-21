package com.kmarko.nasdrive

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
                val context = LocalContext.current

                LaunchedEffect(state.openRequest) {
                    val request = state.openRequest ?: return@LaunchedEffect
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(request.url), request.mimeType)
                        }
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.clearOpenRequest()
                }

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
                        onOpen = { viewModel.openFile(it) },
                        onUpload = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        onRequestDelete = { viewModel.requestDelete(it) },
                        onCancelDelete = { viewModel.cancelDelete() },
                        onConfirmDelete = { viewModel.confirmDelete() },
                        onStartMove = { viewModel.startMove(it) },
                        onCancelMove = { viewModel.cancelMove() },
                        onConfirmMove = { viewModel.confirmMoveHere() },
                        onRequestCreateFolder = { viewModel.requestCreateFolder() },
                        onCancelCreateFolder = { viewModel.cancelCreateFolder() },
                        onConfirmCreateFolder = { viewModel.confirmCreateFolder(it) },
                        onRequestRename = { viewModel.requestRename(it) },
                        onCancelRename = { viewModel.cancelRename() },
                        onConfirmRename = { viewModel.confirmRename(it) },
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onClearSelection = { viewModel.clearSelection() },
                        onDeleteSelected = { viewModel.requestDeleteSelected() },
                        onMoveSelected = { viewModel.startMoveSelected() },
                        onSetSortOption = { viewModel.setSortOption(it) },
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
