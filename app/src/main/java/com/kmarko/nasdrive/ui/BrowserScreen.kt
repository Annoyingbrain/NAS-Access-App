package com.kmarko.nasdrive.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kmarko.nasdrive.CopyState
import com.kmarko.nasdrive.MoveState
import com.kmarko.nasdrive.NasEntry
import com.kmarko.nasdrive.SortOption
import com.kmarko.nasdrive.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onStartCopy: (NasEntry) -> Unit,
    onCancelCopy: () -> Unit,
    onConfirmCopy: () -> Unit,
    onRequestCreateFolder: () -> Unit,
    onCancelCreateFolder: () -> Unit,
    onConfirmCreateFolder: (String) -> Unit,
    onRequestRename: (NasEntry) -> Unit,
    onCancelRename: () -> Unit,
    onConfirmRename: (String) -> Unit,
    onToggleSelection: (NasEntry) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onSetSortOption: (SortOption) -> Unit,
    onDismissMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val moving = state.move != null
    val copying = state.copyState != null

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
            if (state.isSelecting) {
                TopAppBar(
                    title = { Text("${state.selectedNames.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = onCopySelected) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy selected")
                        }
                        IconButton(onClick = onMoveSelected) {
                            Icon(Icons.Filled.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
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
                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name") },
                                    onClick = { onSetSortOption(SortOption.NAME); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date modified") },
                                    onClick = { onSetSortOption(SortOption.DATE_NEWEST); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size") },
                                    onClick = { onSetSortOption(SortOption.SIZE_LARGEST); sortMenuExpanded = false }
                                )
                            }
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onDisconnect) {
                            Icon(Icons.Filled.Logout, contentDescription = "Disconnect")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!moving && !copying && !state.isSelecting) {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(onClick = onRequestCreateFolder) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ExtendedFloatingActionButton(
                        onClick = onUpload,
                        icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                        text = { Text("Upload") }
                    )
                }
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
            } else if (state.copyState != null) {
                CopyBanner(
                    copyState = state.copyState,
                    destination = if (state.currentPath.isBlank()) "/ (root)" else "/${state.currentPath}",
                    onCopyHere = onConfirmCopy,
                    onCancel = onCancelCopy
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
                    // under the floating New folder/Upload buttons, which sit on top of
                    // the list rather than reserving their own space.
                    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                        items(state.displayEntries) { entry ->
                            FileRow(
                                entry = entry,
                                actionsEnabled = !moving && !copying && !state.isSelecting,
                                selecting = state.isSelecting,
                                isSelected = state.selectedNames.contains(entry.name),
                                moving = moving || copying,
                                onOpenFolder = onOpenFolder,
                                onOpen = onOpen,
                                onDownload = onDownload,
                                onDelete = onRequestDelete,
                                onMove = onStartMove,
                                onCopy = onStartCopy,
                                onRename = onRequestRename,
                                onToggleSelection = onToggleSelection
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
    if (pendingDelete.isNotEmpty()) {
        val single = pendingDelete.singleOrNull()
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = {
                Text(
                    if (single != null) "Delete ${if (single.isDirectory) "folder" else "file"}?"
                    else "Delete ${pendingDelete.size} items?"
                )
            },
            text = {
                Text(
                    if (single != null) {
                        if (single.isDirectory) {
                            "\"${single.name}\" and everything inside it will be permanently deleted from the NAS. This can't be undone."
                        } else {
                            "\"${single.name}\" will be permanently deleted from the NAS. This can't be undone."
                        }
                    } else {
                        "${pendingDelete.size} items will be permanently deleted from the NAS. This can't be undone."
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

    if (state.creatingFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onCancelCreateFolder,
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmCreateFolder(name) }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = onCancelCreateFolder) { Text("Cancel") }
            }
        )
    }

    val renameTarget = state.renameTarget
    if (renameTarget != null) {
        var name by remember(renameTarget) { mutableStateOf(renameTarget.name) }
        AlertDialog(
            onDismissRequest = onCancelRename,
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmRename(name) }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = onCancelRename) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MoveBanner(move: MoveState, destination: String, onMoveHere: () -> Unit, onCancel: () -> Unit) {
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val title = if (move.entries.size == 1) {
                "Moving \"${move.entries.first().name}\""
            } else {
                "Moving ${move.entries.size} items"
            }
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("Browse to a folder, then move it here. Currently: $destination", style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onMoveHere) { Text("Move here") }
            }
        }
    }
}

@Composable
private fun CopyBanner(copyState: CopyState, destination: String, onCopyHere: () -> Unit, onCancel: () -> Unit) {
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val title = if (copyState.entries.size == 1) {
                "Copying \"${copyState.entries.first().name}\""
            } else {
                "Copying ${copyState.entries.size} items"
            }
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("Browse to a folder, then copy it here. Currently: $destination", style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onCopyHere) { Text("Copy here") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: NasEntry,
    actionsEnabled: Boolean,
    selecting: Boolean,
    isSelected: Boolean,
    moving: Boolean,
    onOpenFolder: (String) -> Unit,
    onOpen: (NasEntry) -> Unit,
    onDownload: (NasEntry) -> Unit,
    onDelete: (NasEntry) -> Unit,
    onMove: (NasEntry) -> Unit,
    onCopy: (NasEntry) -> Unit,
    onRename: (NasEntry) -> Unit,
    onToggleSelection: (NasEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    when {
                        selecting -> onToggleSelection(entry)
                        entry.isDirectory -> onOpenFolder(entry.name)
                        actionsEnabled -> onOpen(entry)
                    }
                },
                onLongClick = { if (!moving) onToggleSelection(entry) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection(entry) })
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            val subtitle = if (entry.isDirectory) {
                formatDate(entry.lastModified)
            } else {
                "${formatSize(entry.size)} • ${formatDate(entry.lastModified)}"
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        if (actionsEnabled) {
            if (!entry.isDirectory) {
                IconButton(onClick = { onDownload(entry) }) {
                    Icon(Icons.Filled.Download, contentDescription = "Download")
                }
            }
            IconButton(onClick = { onRename(entry) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { onCopy(entry) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
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

private fun formatDate(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
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
