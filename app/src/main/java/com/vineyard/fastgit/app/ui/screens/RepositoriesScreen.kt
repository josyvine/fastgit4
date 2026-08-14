package com.vineyard.fastgit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.fastgit.app.models.Repository
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.RepositoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoriesScreen(
    repositoryViewModel: RepositoryViewModel,
    onSelectRepo: (Repository) -> Unit,
    showCreateDialogInitially: Boolean = false,
    showImportDialogInitially: Boolean = false
) {
    val context = LocalContext.current
    val repositories by repositoryViewModel.repositories.collectAsState()
    val searchQuery by repositoryViewModel.searchQuery.collectAsState()
    val selectedFilter by repositoryViewModel.selectedFilter.collectAsState()
    val isLoading by repositoryViewModel.isLoading.collectAsState()
    val statusMessage by repositoryViewModel.statusMessage.collectAsState()

    var showCreateDialog by remember { mutableStateOf(showCreateDialogInitially) }
    var showImportDialog by remember { mutableStateOf(showImportDialogInitially) }
    var repoToDelete by remember { mutableStateOf<Repository?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val filterOptions = listOf("All", "Public", "Private", "Sources", "Forks")

    val filteredRepos = repositories.filter { repo ->
        val matchesQuery = repo.name.contains(searchQuery, ignoreCase = true) ||
                (repo.description?.contains(searchQuery, ignoreCase = true) == true)

        val matchesFilter = when (selectedFilter) {
            "Public" -> !repo.private
            "Private" -> repo.private
            "Forks" -> repo.fork
            "Sources" -> !repo.fork
            else -> true
        }

        matchesQuery && matchesFilter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Repositories",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showImportDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Import Repo", tint = MaterialTheme.colorScheme.primary)
                    }

                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = GhSuccessGreen,
                        contentColor = Color.White,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Repo")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { repositoryViewModel.onSearchQueryChange(it) },
                placeholder = { Text("Search repositories...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Pills Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { filter ->
                    FilterChip(
                        selected = filter == selectedFilter,
                        onClick = { repositoryViewModel.onFilterSelect(filter) },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.surface,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Repos List Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        coroutineScope.launch {
                            repositoryViewModel.fetchRepositories()
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading && !isRefreshing) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (filteredRepos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching repositories found.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredRepos) { repo ->
                                RepoCardItem(
                                    repo = repo, 
                                    onClick = { onSelectRepo(repo) },
                                    onDeleteClick = { repoToDelete = repo },
                                    onDownloadZipClick = {
                                        repositoryViewModel.downloadRepositoryAsZip(
                                            owner = repo.owner?.login ?: "developer",
                                            repoName = repo.name,
                                            branch = repo.defaultBranch,
                                            context = context
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status Snackbar / Message
        statusMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { repositoryViewModel.clearStatus() }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(msg)
            }
        }
    }

    // Repository Delete Confirmation Dialog
    if (repoToDelete != null) {
        val target = repoToDelete!!
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text("Delete Repository?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${target.fullName}'? This action is permanent and cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repoToDelete = null
                        repositoryViewModel.deleteRepository(target.owner?.login ?: "developer", target.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Create Repository Dialog
    if (showCreateDialog) {
        CreateRepoDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, isPrivate, readme, gitignore, license ->
                showCreateDialog = false
                repositoryViewModel.createRepository(name, desc, isPrivate, readme, gitignore, license) { newRepo ->
                    onSelectRepo(newRepo)
                }
            }
        )
    }

    // Import Repository Dialog
    if (showImportDialog) {
        ImportRepoDialog(
            onDismiss = { showImportDialog = false },
            onImport = { url, newRepoName, isPrivate ->
                showImportDialog = false
                repositoryViewModel.importRepositoryUrl(url, newRepoName, isPrivate) { imported ->
                    onSelectRepo(imported)
                }
            }
        )
    }
}

@Composable
fun CreateRepoDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, isPrivate: Boolean, readme: Boolean, gitignore: String?, license: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var initReadme by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Repository", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Private Repository", color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Initialize with README", color = MaterialTheme.colorScheme.onSurface)
                    Checkbox(
                        checked = initReadme,
                        onCheckedChange = { initReadme = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, desc, isPrivate, initReadme, "Android", "MIT") },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)
            ) {
                Text("Create", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun ImportRepoDialog(
    onDismiss: () -> Unit,
    onImport: (url: String, newRepoName: String, isPrivate: Boolean) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var newRepoName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Repository", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste a GitHub repository URL and choose a new repository name for your imported copy.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { inputUrl ->
                        val clean = inputUrl.trim().removeSuffix("/").removeSuffix(".git")
                        val extracted = clean.substringAfterLast("/")
                        if (extracted.isNotBlank() && !extracted.contains(":") && !extracted.contains("?")) {
                            if (newRepoName.isBlank() || clean.endsWith(newRepoName)) {
                                newRepoName = extracted
                            }
                        }
                        url = inputUrl
                    },
                    label = { Text("GitHub Source URL *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newRepoName,
                    onValueChange = { newRepoName = it },
                    label = { Text("New Repository Name *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Private Repository", color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(url, newRepoName, isPrivate) },
                enabled = url.isNotBlank() && newRepoName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Import", color = MaterialTheme.colorScheme.surface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}