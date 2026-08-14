package com.vineyard.fastgit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vineyard.fastgit.app.models.*
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.RepoDetailViewModel
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RepoDetailScreen(
    repoDetailViewModel: RepoDetailViewModel,
    onBack: () -> Unit
) {
    val repository by repoDetailViewModel.repository.collectAsState()
    val branches by repoDetailViewModel.branches.collectAsState()
    val currentBranch by repoDetailViewModel.currentBranch.collectAsState()
    val statusMessage by repoDetailViewModel.statusMessage.collectAsState()

    val isUploadingZip by repoDetailViewModel.isUploadingZip.collectAsState()
    val uploadStep by repoDetailViewModel.uploadStep.collectAsState()
    val uploadProgress by repoDetailViewModel.uploadProgress.collectAsState()

    // Smart Refactoring Progress State Collectors
    val isRefactoring by repoDetailViewModel.isRefactoring.collectAsState()
    val refactorStep by repoDetailViewModel.refactorStep.collectAsState()
    val refactorProgress by repoDetailViewModel.refactorProgress.collectAsState()

    // Artifact Download Progress State Collectors
    val isDownloadingArtifact by repoDetailViewModel.isDownloadingArtifact.collectAsState()
    val artifactDownloadStep by repoDetailViewModel.artifactDownloadStep.collectAsState()
    val artifactDownloadProgress by repoDetailViewModel.artifactDownloadProgress.collectAsState()

    val activeFile by repoDetailViewModel.activeFile.collectAsState()
    val fileContent by repoDetailViewModel.fileContent.collectAsState()

    // Logs states
    var selectedRunForLogs by remember { mutableStateOf<WorkflowRun?>(null) }
    val workflowLogs by repoDetailViewModel.workflowLogs.collectAsState()
    val isLogsLoading by repoDetailViewModel.isLogsLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Explorer", "Branches", "Commits", "PRs", "Issues", "Actions", "Releases", "Settings")

    var isExplorerMaximized by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // File launcher for ZIP upload
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { repoDetailViewModel.uploadProjectZip(it, context) }
    }

    if (activeFile != null) {
        CodeEditorScreen(
            fileItem = activeFile!!,
            initialContent = fileContent,
            onBack = { repoDetailViewModel.closeActiveFile() },
            onSaveAndCommit = { updatedContent, commitMsg ->
                repoDetailViewModel.saveAndCommitFile(activeFile!!, updatedContent, commitMsg)
            },
            onDownloadClick = { content ->
                repoDetailViewModel.downloadSingleFileToDevice(activeFile!!, content, context)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            if (!isExplorerMaximized || selectedTab != 0) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = repository?.name ?: repoDetailViewModel.repoName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${repoDetailViewModel.owner} • branch: $currentBranch",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        var branchMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(
                                onClick = { branchMenuExpanded = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(currentBranch, fontSize = 13.sp)
                            }

                            DropdownMenu(
                                expanded = branchMenuExpanded,
                                onDismissRequest = { branchMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                branches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            branchMenuExpanded = false
                                            repoDetailViewModel.switchBranch(branch.name)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isExplorerMaximized && selectedTab == 0) PaddingValues(0.dp) else innerPadding)
        ) {
            if (!isExplorerMaximized || selectedTab != 0) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 12.dp
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val tabColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = tabColor
                                )
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ExplorerTabContent(
                        repoDetailViewModel = repoDetailViewModel,
                        onUploadZipClick = { zipPickerLauncher.launch("application/zip") },
                        isMaximized = isExplorerMaximized,
                        onToggleMaximize = { isExplorerMaximized = !isExplorerMaximized }
                    )
                    1 -> BranchesTabContent(repoDetailViewModel)
                    2 -> CommitsTabContent(repoDetailViewModel)
                    3 -> PRsTabContent(repoDetailViewModel)
                    4 -> IssuesTabContent(repoDetailViewModel)
                    5 -> ActionsTabContent(
                        repoDetailViewModel = repoDetailViewModel,
                        onShowLogViewer = { run ->
                            repoDetailViewModel.fetchWorkflowRunLogs(run.id)
                            selectedRunForLogs = run
                        }
                    )
                    6 -> ReleasesTabContent(repoDetailViewModel)
                    7 -> RepoSettingsTabContent(repoDetailViewModel, onBack)
                }
            }
        }
    }

    // Workflow Build Logs Overlay Dialog
    if (selectedRunForLogs != null) {
        var showLogsContextMenu by remember { mutableStateOf(false) }
        Dialog(
            onDismissRequest = {
                selectedRunForLogs = null
                repoDetailViewModel.clearWorkflowLogs()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Build Logs: Run #${selectedRunForLogs?.runNumber}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (selectedRunForLogs?.status == "completed" && selectedRunForLogs?.conclusion == "success") {
                                var showConfirmDownloadDialog by remember { mutableStateOf(false) }
                                
                                IconButton(
                                    onClick = { showConfirmDownloadDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Artifacts",
                                        tint = GhSuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (showConfirmDownloadDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showConfirmDownloadDialog = false },
                                        title = { Text("Download Build Artifacts?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                                        text = { Text("Do you want to download and extract the build APK artifacts from this run to your local storage?", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    showConfirmDownloadDialog = false
                                                    selectedRunForLogs?.let { run ->
                                                        repoDetailViewModel.downloadWorkflowArtifacts(run.id, context)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)
                                            ) {
                                                Text("Download", color = Color.White)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showConfirmDownloadDialog = false }) {
                                                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        },
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    selectedRunForLogs = null
                                    repoDetailViewModel.clearWorkflowLogs()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Logs",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Monospace Log Console Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .background(Color(0xFF04060A), RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { showLogsContextMenu = true }
                            )
                            .padding(12.dp)
                    ) {
                        if (isLogsLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            val verticalScroll = rememberScrollState()
                            Text(
                                text = workflowLogs ?: "Build parameters requested. Awaiting actions context...",
                                color = Color(0xFFC9D1D9),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(verticalScroll)
                            )
                        }

                        DropdownMenu(
                            expanded = showLogsContextMenu,
                            onDismissRequest = { showLogsContextMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy Build Logs", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showLogsContextMenu = false
                                    clipboardManager.setText(AnnotatedString(workflowLogs ?: ""))
                                    Toast.makeText(context, "Build logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Download Build Logs", color = GhSuccessGreen) },
                                onClick = {
                                    showLogsContextMenu = false
                                    selectedRunForLogs?.let { run ->
                                        repoDetailViewModel.downloadWorkflowRunLogs(run.id, run.runNumber, context)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = GhSuccessGreen) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Artifact Download Progress Dialog
    if (isDownloadingArtifact) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = GhSuccessGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Downloading Artifacts",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Downloading and extracting build APK artifacts to Downloads/FastGit...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    if (artifactDownloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { artifactDownloadProgress!! },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = GhSuccessGreen,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = GhSuccessGreen,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = artifactDownloadStep,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (artifactDownloadProgress != null) {
                            Text(
                                text = "${((artifactDownloadProgress ?: 0f) * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // ZIP Upload Progress Dialog
    if (isUploadingZip) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Uploading Android Project ZIP", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Extracting, scanning, and preserving folder hierarchy on GitHub...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = GhSuccessGreen,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uploadStep,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(uploadProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { repoDetailViewModel.cancelZipUpload() }) {
                    Text("Cancel", color = GhErrorRed)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Smart Refactoring Progress Dialog
    if (isRefactoring) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Refactoring Project Structure", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Scanning, renaming namespace references, and moving physical directory folders on GitHub...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    LinearProgressIndicator(
                        progress = { refactorProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = GhSuccessGreen,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = refactorStep,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(refactorProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Status Message Snackbar
    statusMessage?.let { msg ->
        LaunchedEffect(msg) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerTabContent(
    repoDetailViewModel: RepoDetailViewModel,
    onUploadZipClick: () -> Unit,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit
) {
    val treeItems by repoDetailViewModel.treeItems.collectAsState()
    val currentPath by repoDetailViewModel.currentPath.collectAsState()
    val copiedItem by repoDetailViewModel.copiedItem.collectAsState()
    val isLoading by repoDetailViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var explorerMode by remember { mutableStateOf(0) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showSearchReplaceDialog by remember { mutableStateOf(false) }

    var targetPathForAction by remember { mutableStateOf("") }

    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            repoDetailViewModel.uploadSingleFileToDirectory(it, targetPathForAction, context)
        }
    }

    var renameTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargetItem by remember { mutableStateOf<FileItem?>(null) }

    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    fun findNodeByPath(nodes: List<FileItem>, path: String): FileItem? {
        if (path.isEmpty()) return null
        for (node in nodes) {
            if (node.path == path) return node
            if (node.children.isNotEmpty()) {
                val found = findNodeByPath(node.children, path)
                if (found != null) return found
            }
        }
        return null
    }

    val visibleItems = remember(treeItems, expandedPaths, explorerMode, currentPath) {
        if (explorerMode == 1) {
            if (currentPath.isEmpty()) {
                treeItems
            } else {
                val node = findNodeByPath(treeItems, currentPath)
                node?.children ?: emptyList()
            }
        } else {
            val flatList = mutableListOf<FileItem>()

            fun flatten(nodes: List<FileItem>, level: Int) {
                for (node in nodes) {
                    val isExpanded = expandedPaths.contains(node.path)
                    val nodeCopy = node.copy(
                        level = level,
                        isExpanded = isExpanded
                    )

                    flatList.add(nodeCopy)

                    if (node.type == "dir" && isExpanded && node.children.isNotEmpty()) {
                        flatten(node.children, level + 1)
                    }
                }
            }

            flatten(treeItems, level = 0)
            flatList
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                repoDetailViewModel.refreshExplorer()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isMaximized) Modifier.statusBarsPadding() else Modifier)
                .padding(12.dp)
        ) {
            if (!isMaximized) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onUploadZipClick,
                                colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { showSearchReplaceDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.FindReplace, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refactor / Replace", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }

                            OutlinedButton(
                                onClick = {
                                    targetPathForAction = currentPath
                                    showNewFileDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = GhSuccessGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New File", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        if (copiedItem != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Copied: ${copiedItem?.name}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { repoDetailViewModel.pasteCopiedItem(currentPath) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Paste to /${currentPath.ifEmpty { "root" }}", fontSize = 11.sp, color = GhSuccessGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    Surface(
                        onClick = { explorerMode = 0 },
                        color = if (explorerMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = if (explorerMode == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tree View",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (explorerMode == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Surface(
                        onClick = { explorerMode = 1 },
                        color = if (explorerMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (explorerMode == 1) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Folder View",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (explorerMode == 1) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                var isLocalSearchActive by remember { mutableStateOf(false) }
                var localSearchQuery by remember { mutableStateOf("") }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    if (isLocalSearchActive) {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            placeholder = { Text("Search file/folder...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (localSearchQuery.isNotBlank()) {
                                                repoDetailViewModel.searchAndJumpToPath(localSearchQuery)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Submit Search",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isLocalSearchActive = false
                                            localSearchQuery = ""
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel Search",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        IconButton(
                            onClick = { isLocalSearchActive = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Directory",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (explorerMode == 0) {
                            Text(
                                text = "Tap arrow to expand inline",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BreadcrumbBar(
                currentPath = currentPath,
                onNavigatePath = { targetPath -> repoDetailViewModel.navigateToDirectory(targetPath) },
                isMaximized = isMaximized,
                onToggleMaximize = onToggleMaximize,
                onCreateFileClick = {
                    targetPathForAction = currentPath
                    showNewFileDialog = true
                },
                onUploadFileClick = {
                    targetPathForAction = currentPath
                    singleFilePickerLauncher.launch("*/*")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (currentPath.isNotEmpty()) {
                    item(key = "parent_folder_nav_up") {
                        ParentFolderNodeRow(
                            currentPath = currentPath,
                            onNavigateUp = {
                                val parentPath = if (currentPath.contains("/")) currentPath.substringBeforeLast("/") else ""
                                repoDetailViewModel.navigateToDirectory(parentPath)
                            }
                        )
                    }
                }

                if (visibleItems.isEmpty() && !isLoading) {
                    item(key = "empty_directory_banner") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (currentPath.isEmpty()) "This repository is empty" else "This folder is empty",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (currentPath.isEmpty()) 
                                        "No files found on branch. Upload a ZIP project or add a file to get started." 
                                    else 
                                        "No files found in /$currentPath",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onUploadZipClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            targetPathForAction = currentPath
                                            showNewFileDialog = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = GhSuccessGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New File", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }

                items(visibleItems, key = { "tree_${it.type}_${it.path}" }) { item ->
                    TreeItemNodeRow(
                        item = item,
                        explorerMode = explorerMode,
                        copiedItem = copiedItem,
                        repoDetailViewModel = repoDetailViewModel,
                        onItemClick = { target ->
                            if (target.type == "dir") {
                                if (explorerMode == 1) {
                                    repoDetailViewModel.navigateToDirectory(target.path)
                                } else {
                                    val isExp = expandedPaths.contains(target.path)
                                    if (!isExp && target.children.isEmpty()) {
                                        repoDetailViewModel.fetchSubfolderContents(target.path)
                                    }
                                    expandedPaths = if (isExp) expandedPaths - target.path else expandedPaths + target.path
                                }
                            } else {
                                repoDetailViewModel.openFile(target)
                            }
                        },
                        onOpenFolderDirect = { folder ->
                            repoDetailViewModel.navigateToDirectory(folder.path)
                        },
                        onCreateFileInFolder = { folder ->
                            targetPathForAction = folder.path
                            showNewFileDialog = true
                        },
                        onUploadFileToFolder = { folder ->
                            targetPathForAction = folder.path
                            singleFilePickerLauncher.launch("*/*")
                        },
                        onCopyItem = { fileItem -> repoDetailViewModel.copyItem(fileItem) },
                        onPasteIntoFolder = { folderPath -> repoDetailViewModel.pasteCopiedItem(folderPath) },
                        onRenameItem = { fileItem -> renameTargetItem = fileItem },
                        onDeleteItem = { fileItem -> deleteTargetItem = fileItem },
                        onDownloadFolderZip = { folder ->
                            repoDetailViewModel.downloadFolderAsZip(folder, context)
                        }
                    )
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        var initialCode by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val activeDirText = if (targetPathForAction.isBlank()) "root" else "/$targetPathForAction"
                    Text(
                        text = "Creating file inside: $activeDirText",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name (e.g. MyClass.kt)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = initialCode,
                        onValueChange = { initialCode = it },
                        label = { Text("Initial Content (optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewFileDialog = false
                        repoDetailViewModel.createNewFileInDirectory(targetPathForAction, newFileName, initialCode, "Create $newFileName via FastGit")
                    },
                    enabled = newFileName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)
                ) {
                    Text("Create File", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Rename Dialog
    if (renameTargetItem != null) {
        val target = renameTargetItem!!
        var newNameInput by remember { mutableStateOf(target.name) }

        AlertDialog(
            onDismissRequest = { renameTargetItem = null },
            title = { Text("Rename ${target.type.replaceFirstChar { it.uppercase() }}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current path: /${target.path}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("New Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = target
                        renameTargetItem = null
                        repoDetailViewModel.renameItem(t, newNameInput)
                    },
                    enabled = newNameInput.isNotBlank() && newNameInput != target.name,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetItem = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Delete Confirmation Dialog
    if (deleteTargetItem != null) {
        val target = deleteTargetItem!!

        AlertDialog(
            onDismissRequest = { deleteTargetItem = null },
            title = { Text("Delete ${target.name}?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete '${target.path}' from this repository? This will commit a deletion on branch.", color = MaterialTheme.colorScheme.onSurface)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = target
                        deleteTargetItem = null
                        repoDetailViewModel.deleteItem(t)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetItem = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Global Search & Replace Dialog
    if (showSearchReplaceDialog) {
        var searchQueryInput by remember { mutableStateOf("") }
        var replaceQueryInput by remember { mutableStateOf("") }
        var isSmartRefactorEnabled by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSearchReplaceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FindReplace, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Global Search & Replace", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Refactor package names or search/replace code across all files in repository.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = searchQueryInput,
                        onValueChange = { searchQueryInput = it },
                        label = { Text("Search string (e.g. com.oldpackage)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = replaceQueryInput,
                        onValueChange = { replaceQueryInput = it },
                        label = { Text("Replace with (e.g. com.newpackage)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSmartRefactorEnabled = !isSmartRefactorEnabled }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = isSmartRefactorEnabled,
                            onCheckedChange = { isSmartRefactorEnabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Smart Package Refactor",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Moves physical folders to match the new package structure.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sq = searchQueryInput
                        val rq = replaceQueryInput
                        showSearchReplaceDialog = false
                        if (isSmartRefactorEnabled) {
                            repoDetailViewModel.performSmartPackageRefactor(sq, rq) { _, _ -> }
                        } else {
                            repoDetailViewModel.performGlobalSearchAndReplace(sq, rq) { _, _ -> }
                        }
                    },
                    enabled = searchQueryInput.isNotBlank() && replaceQueryInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Replace All Occurrences", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchReplaceDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeItemNodeRow(
    item: FileItem,
    explorerMode: Int,
    copiedItem: FileItem?,
    repoDetailViewModel: RepoDetailViewModel,
    onItemClick: (FileItem) -> Unit,
    onOpenFolderDirect: (FileItem) -> Unit,
    onCreateFileInFolder: (FileItem) -> Unit,
    onUploadFileToFolder: (FileItem) -> Unit,
    onCopyItem: (FileItem) -> Unit,
    onPasteIntoFolder: (String) -> Unit,
    onRenameItem: (FileItem) -> Unit,
    onDeleteItem: (FileItem) -> Unit,
    onDownloadFolderZip: (FileItem) -> Unit
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    var showPlusMenu by remember { mutableStateOf(false) }

    val icon = if (item.type == "dir") {
        if (item.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
    } else {
        Icons.Default.Description
    }

    val iconTint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onItemClick(item) },
                    onLongClick = { showContextMenu = true }
                ),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        horizontal = if (explorerMode == 0) (12 + item.level * 14).dp else 12.dp,
                        vertical = 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (item.type == "dir") {
                    Box {
                        IconButton(
                            onClick = { showPlusMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add or Upload File",
                                tint = GhSuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showPlusMenu,
                            onDismissRequest = { showPlusMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Create File", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showPlusMenu = false
                                    onCreateFileInFolder(item)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.NoteAdd,
                                        contentDescription = null,
                                        tint = GhSuccessGreen
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Upload File", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showPlusMenu = false
                                    onUploadFileToFolder(item)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = { onOpenFolderDirect(item) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SubdirectoryArrowRight,
                            contentDescription = "Navigate into folder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (explorerMode == 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (item.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            if (item.type == "dir") {
                DropdownMenuItem(
                    text = { Text("Open Folder", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        onOpenFolderDirect(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Create File Here", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        onCreateFileInFolder(item)
                    },
                    leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, tint = GhSuccessGreen) }
                )
                DropdownMenuItem(
                    text = { Text("Upload File Here", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        onUploadFileToFolder(item)
                    },
                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                if (copiedItem != null) {
                    DropdownMenuItem(
                        text = { Text("Paste '${copiedItem.name}' Here", color = GhSuccessGreen) },
                        onClick = {
                            showContextMenu = false
                            onPasteIntoFolder(item.path)
                        },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = GhSuccessGreen) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy Folder Path", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        val deepestPath = repoDetailViewModel.getDeepestSingleDirectoryPath(item)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Folder Path", deepestPath)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Folder path copied to clipboard!", Toast.LENGTH_SHORT).show()
                        onCopyItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        onRenameItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = Color.Red) },
                    onClick = {
                        showContextMenu = false
                        onDeleteItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
                DropdownMenuItem(
                    text = { Text("Download Folder as ZIP", color = GhSuccessGreen) },
                    onClick = {
                        showContextMenu = false
                        onDownloadFolderZip(item)
                    },
                    leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = GhSuccessGreen) }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Edit / View Code", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        onItemClick(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Copy File", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("File Path", item.path)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "File path copied to clipboard!", Toast.LENGTH_SHORT).show()
                        onCopyItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Rename File", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showContextMenu = false
                        onRenameItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Delete File", color = Color.Red) },
                    onClick = {
                        showContextMenu = false
                        onDeleteItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
            }
        }
    }
}

@Composable
fun ParentFolderNodeRow(
    currentPath: String,
    onNavigateUp: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateUp() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DriveFileMove,
                contentDescription = "Parent Directory",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ".. (Parent Directory)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Go up from /$currentPath",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun BreadcrumbBar(
    currentPath: String,
    onNavigatePath: (String) -> Unit,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    onCreateFileClick: () -> Unit,
    onUploadFileClick: () -> Unit
) {
    var showPlusMenu by remember { mutableStateOf(false) }

    val segments = remember(currentPath) {
        if (currentPath.isBlank()) {
            listOf(BreadcrumbSegment("root", ""))
        } else {
            val parts = currentPath.split("/").filter { it.isNotBlank() }
            val list = mutableListOf(BreadcrumbSegment("root", ""))
            var accum = ""
            for (part in parts) {
                accum = if (accum.isEmpty()) part else "$accum/$part"
                list.add(BreadcrumbSegment(part, accum))
            }
            list
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))

                androidx.compose.foundation.lazy.LazyRow(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(segments.size) { index ->
                        val seg = segments[index]
                        val isLast = index == segments.size - 1

                        if (index > 0) {
                            Text(
                                text = "/",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            onClick = { onNavigatePath(seg.path) },
                            color = if (isLast) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = seg.name,
                                fontSize = 12.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { showPlusMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add or Upload File",
                            tint = GhSuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showPlusMenu,
                        onDismissRequest = { showPlusMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create File", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showPlusMenu = false
                                onCreateFileClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.NoteAdd,
                                    contentDescription = null,
                                    tint = GhSuccessGreen
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Upload File", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showPlusMenu = false
                                onUploadFileClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleMaximize,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isMaximized) "Minimize View" else "Maximize View",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

data class BreadcrumbSegment(val name: String, val path: String)

@Composable
fun BranchesTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val branches by repoDetailViewModel.branches.collectAsState()
    val currentBranch by repoDetailViewModel.currentBranch.collectAsState()

    var showCreateBranchDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Branch Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

            Button(
                onClick = { showCreateBranchDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Branch", color = MaterialTheme.colorScheme.surface)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(branches) { branch ->
                val isSelected = branch.name == currentBranch
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CallSplit, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(branch.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Surface(
                                color = GhSuccessGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Active", color = GhSuccessGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        } else {
                            TextButton(onClick = { repoDetailViewModel.switchBranch(branch.name) }) {
                                Text("Switch", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateBranchDialog) {
        var branchName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateBranchDialog = false },
            title = { Text("Create Branch", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text("Branch Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateBranchDialog = false
                        repoDetailViewModel.createBranch(branchName)
                    },
                    enabled = branchName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Create", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBranchDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun CommitsTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val commits by repoDetailViewModel.commits.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(commits) { commit ->
            CommitCardItem(commit = commit)
        }
    }
}

@Composable
fun PRsTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val pullRequests by repoDetailViewModel.pullRequests.collectAsState()
    var showCreatePRDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Pull Requests", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = { showCreatePRDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet)) {
                Text("New PR", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pullRequests) { pr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CallMerge, contentDescription = null, tint = GhPrimaryViolet)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${pr.number} ${pr.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text("opened by ${pr.user?.login ?: "user"} • ${pr.createdAt ?: "recently"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (showCreatePRDialog) {
        var prTitle by remember { mutableStateOf("") }
        var prBody by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePRDialog = false },
            title = { Text("Create Pull Request", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = prTitle, onValueChange = { prTitle = it }, label = { Text("Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = prBody, onValueChange = { prBody = it }, label = { Text("Body") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCreatePRDialog = false
                    repoDetailViewModel.createPullRequest(prTitle, "feature/ui", "main", prBody)
                }, enabled = prTitle.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet)) {
                    Text("Submit PR", color = Color.White)
                }
            },
            dismissButton = { TextButton(onClick = { showCreatePRDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun IssuesTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val issues by repoDetailViewModel.issues.collectAsState()
    var showCreateIssueDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Issues Tracker", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = { showCreateIssueDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)) {
                Text("New Issue", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(issues) { issue ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = GhSuccessGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${issue.number} ${issue.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text("opened by ${issue.user?.login ?: "user"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (showCreateIssueDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateIssueDialog = false },
            title = { Text("Create Issue", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Issue Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Description") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCreateIssueDialog = false
                    repoDetailViewModel.createIssue(title, body)
                }, enabled = title.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = { TextButton(onClick = { showCreateIssueDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActionsTabContent(
    repoDetailViewModel: RepoDetailViewModel,
    onShowLogViewer: (WorkflowRun) -> Unit
) {
    val workflows by repoDetailViewModel.workflows.collectAsState()
    val workflowRuns by repoDetailViewModel.workflowRuns.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                repoDetailViewModel.refreshWorkflows()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("GitHub Actions Workflows", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }

            items(workflows) { wf ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(wf.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text(wf.path, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Button(onClick = { repoDetailViewModel.triggerWorkflow(wf.id) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("Run", fontSize = 12.sp, color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }

            item {
                Text("Recent Workflow Runs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }

            items(workflowRuns) { run ->
                var runMenuExpanded by remember { mutableStateOf(false) }
                val isRunning = run.status == "in_progress" || run.status == "queued"

                val runTitle = run.displayTitle?.ifBlank { null }
                    ?: run.headCommit?.message?.lines()?.firstOrNull()?.ifBlank { null }
                    ?: run.name
                    ?: "Workflow Run #${run.runNumber}"

                val runWorkflowName = run.name?.ifBlank { null } ?: "Build APK"

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onShowLogViewer(run) },
                                onLongClick = { runMenuExpanded = true }
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (run.conclusion == "success") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (run.conclusion == "success") GhSuccessGreen else GhErrorRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = runTitle,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$runWorkflowName #${run.runNumber} • branch: ${run.headBranch ?: "main"} • status: ${run.status}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = runMenuExpanded,
                        onDismissRequest = { runMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy Run Logs", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                runMenuExpanded = false
                                repoDetailViewModel.copyWorkflowLogsDirect(run.id, context)
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Download Run Logs", color = GhSuccessGreen) },
                            onClick = {
                                runMenuExpanded = false
                                repoDetailViewModel.downloadWorkflowRunLogs(run.id, run.runNumber, context)
                            },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = GhSuccessGreen) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReleasesTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val releases by repoDetailViewModel.releases.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Releases & Assets", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        items(releases) { release ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("${release.tagName} - ${release.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text(release.body ?: "No release notes provided", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun RepoSettingsTabContent(repoDetailViewModel: RepoDetailViewModel, onBack: () -> Unit) {
    val repository by repoDetailViewModel.repository.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Repository Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Metadata", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Name: ${repository?.name}", color = MaterialTheme.colorScheme.onSurface)
                Text("Full Name: ${repository?.fullName}", color = MaterialTheme.colorScheme.onSurface)
                Text("Visibility: ${if (repository?.private == true) "Private" else "Public"}", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

