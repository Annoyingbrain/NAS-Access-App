package com.kmarko.nasdrive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kmarko.nasdrive.MoveState
import com.kmarko.nasdrive.NasEntry
import com.kmarko.nasdrive.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: UiState,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onDownload: (NasEntry) -> Unit,
    onOpen: (NasEntry) -> Unit,
    onUpload: () -> Unit,
    onRequestDelete: (NasEntry) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onStartMove: (NasEntry) -> Unit,
    onCancelMove: () -> Unit,
    onConfirmMove: () -> Unit,
    onDismissMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val moving = state.move != null

    LaunchedEffect(state.errorMessage, state.statusMessage) {
        val message = state.errorMessage ?: state.statusMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.currentPath.isBlank()) "/ (root)" else "/${state.currentPath}") },
                navigationIcon = {
                    if (state.currentPath.isNotBlank()) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Filled.Logout, contentDescription = "Disconnect")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!moving) {
                ExtendedFloatingActionButton(
                    onClick = onUpload,
                    icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    text = { Text("Upload") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.move != null) {
                MoveBanner(
                    move = state.move,
                    destination = if (state.currentPath.isBlank()) "/ (root)" else "/${state.currentPath}",
                    onMoveHere = onConfirmMove,
                    onCancel = onCancelMove
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (state.entries.isEmpty()) {
                    Text("This folder is empty", modifier = Modifier.align(Alignment.Center))
                } else {
                    // Bottom padding keeps the last row(s) from being permanently hidden
                    // under the floating Upload button, which sits on top of the list
                    // rather than reserving its own space.
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(state.entries) { entry ->
                            FileRow(
                                entry = entry,
                                actionsEnabled = !moving,
                                onOpenFolder = onOpenFolder,
                                onOpen = onOpen,
                                onDownload = onDownload,
                                onDelete = onRequestDelete,
                                onMove = onStartMove
                            )
                            HorizontalDivider()
                        }
                    }
                }

                if (state.transfer.active) {
                    TransferBanner(
                        fileName = state.transfer.fileName,
                        isUpload = state.transfer.isUpload,
                        bytesDone = state.transfer.bytesDone,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    val pendingDelete = state.pendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete ${if (pendingDelete.isDirectory) "folder" else "file"}?") },
            text = {
                Text(
                    if (pendingDelete.isDirectory) {
                        "\"${pendingDelete.name}\" and everything inside it will be permanently deleted from the NAS. This can't be undone."
                    } else {
                        "\"${pendingDelete.name}\" will be permanently deleted from the NAS. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MoveBanner(move: MoveState, destination: String, onMoveHere: () -> Unit, onCancel: () -> Unit) {
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Moving \"${move.entry.name}\"", style = MaterialTheme.typography.bodyLarge)
            Text("Browse to a folder, then move it here. Currently: $destination", style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onMoveHere) { Text("Move here") }
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: NasEntry,
    actionsEnabled: Boolean,
    onOpenFolder: (String) -> Unit,
    onOpen: (NasEntry) -> Unit,
    onDownload: (NasEntry) -> Unit,
    onDelete: (NasEntry) -> Unit,
    onMove: (NasEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isDirectory || actionsEnabled) {
                if (entry.isDirectory) onOpenFolder(entry.name) else onOpen(entry)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            if (!entry.isDirectory) {
                Text(formatSize(entry.size), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (actionsEnabled) {
            if (!entry.isDirectory) {
                IconButton(onClick = { onDownload(entry) }) {
                    Icon(Icons.Filled.Download, contentDescription = "Download")
                }
            }
            IconButton(onClick = { onMove(entry) }) {
                Icon(Icons.Filled.DriveFileMove, contentDescription = "Move")
            }
            IconButton(onClick = { onDelete(entry) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun TransferBanner(fileName: String, isUpload: Boolean, bytesDone: Long, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "${if (isUpload) "Uploading" else "Downloading"} $fileName (${formatSize(bytesDone)})",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}
