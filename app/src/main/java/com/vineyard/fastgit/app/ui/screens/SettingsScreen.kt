package com.vineyard.fastgit.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.fastgit.app.models.Repository
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.AuthViewModel
import com.vineyard.fastgit.app.viewmodel.SettingsViewModel

private data class SettingsTab(val label: String, val icon: ImageVector)

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val cacheSize by settingsViewModel.cacheSize.collectAsState()

    // Propagation Feature States
    val repositories by settingsViewModel.repositories.collectAsState()
    val savedAliases by settingsViewModel.savedAliases.collectAsState()
    val statusMessage by settingsViewModel.statusMessage.collectAsState()
    val isLoading by settingsViewModel.isLoading.collectAsState()

    // Raw URL Downloader Feature States
    val repoDirectories by settingsViewModel.repoDirectories.collectAsState()
    val isDownloadingUrls by settingsViewModel.isDownloadingUrls.collectAsState()
    val downloadStep by settingsViewModel.downloadStep.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }

    // Local Form Input States
    var showCreateForm by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }
    var keystoreBase64Input by remember { mutableStateOf("") }
    var keystorePasswordInput by remember { mutableStateOf("") }
    var keyAliasInput by remember { mutableStateOf("") }
    var keyPasswordInput by remember { mutableStateOf("") }

    // Local Dropdowns States (Secret Propagation)
    var selectedRepo by remember { mutableStateOf<Repository?>(null) }
    var selectedAlias by remember { mutableStateOf("") }
    var repoDropdownExpanded by remember { mutableStateOf(false) }
    var aliasDropdownExpanded by remember { mutableStateOf(false) }

    // Local Dropdowns States (Raw URL Downloader)
    var selectedDownloaderRepo by remember { mutableStateOf<Repository?>(null) }
    var selectedDownloaderDir by remember { mutableStateOf("") }
    var downloaderRepoDropdownExpanded by remember { mutableStateOf(false) }
    var downloaderDirDropdownExpanded by remember { mutableStateOf(false) }

    // Navigation Tab Selection State
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        SettingsTab("App", Icons.Default.Tune),
        SettingsTab("Keys", Icons.Default.VpnKey),
        SettingsTab("URLs", Icons.Default.CloudDownload),
        SettingsTab("Auth", Icons.Default.Security),
        SettingsTab("About", Icons.Default.Info)
    )

    // Status / Messages Handler
    statusMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            settingsViewModel.clearStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen Title Area
        Text(
            text = "Settings",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // Tab Selector Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTabIndex == index
                val tabColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                Tab(
                    selected = isSelected,
                    onClick = { selectedTabIndex = index },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tabColor,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = tabColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        // Active Section Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    // General Preferences Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "App Preferences",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )

                            SettingsRow(
                                icon = Icons.Default.Palette,
                                title = "Theme Mode",
                                subtitle = themeMode,
                                onClick = {
                                    val next = when (themeMode.trim().lowercase()) {
                                        "dark" -> "Light"
                                        "light" -> "System"
                                        else -> "Dark"
                                    }
                                    settingsViewModel.setTheme(next)
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsRow(
                                icon = Icons.Default.FolderZip,
                                title = "Downloads Directory",
                                subtitle = "Internal Storage / Downloads / FastGit",
                                onClick = {}
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsRow(
                                icon = Icons.Default.CleaningServices,
                                title = "Clear Cache",
                                subtitle = "Cached trees and offline DB ($cacheSize)",
                                onClick = { settingsViewModel.clearCache() }
                            )
                        }
                    }
                }

                1 -> {
                    // Keystore & Secrets Propagation Manager Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Automated Keystore Secrets",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "Persist build credentials locally and auto-propagate them as Actions Secrets to newly imported repositories.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Sub-Section 1: Propagate to GitHub Repository Form
                            Text(
                                text = "Propagate Credentials to GitHub",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Select Target Repository Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { repoDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedRepo?.fullName ?: "Select Target Repository",
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                DropdownMenu(
                                    expanded = repoDropdownExpanded,
                                    onDismissRequest = { repoDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .fillMaxWidth(0.85f)
                                ) {
                                    repositories.forEach { repo ->
                                        DropdownMenuItem(
                                            text = { Text(repo.fullName, color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                selectedRepo = repo
                                                repoDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Select Keystore Profile Alias Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { aliasDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (selectedAlias.isEmpty()) "Select Keystore Alias Profile" else selectedAlias,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                DropdownMenu(
                                    expanded = aliasDropdownExpanded,
                                    onDismissRequest = { aliasDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .fillMaxWidth(0.85f)
                                ) {
                                    savedAliases.forEach { alias ->
                                        DropdownMenuItem(
                                            text = { Text(alias, color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                selectedAlias = alias
                                                aliasDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Deploy Propagation Action Button
                            Button(
                                onClick = {
                                    selectedRepo?.let { repo ->
                                        settingsViewModel.propagateKeystoreToRepository(
                                            targetRepoOwner = repo.owner?.login ?: "",
                                            targetRepoName = repo.name,
                                            profileAlias = selectedAlias
                                        )
                                    }
                                },
                                enabled = selectedRepo != null && selectedAlias.isNotEmpty() && !isLoading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GhSuccessGreen,
                                    disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Propagate Keystore Secrets", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Sub-Section 2: Expandable Form to Save a New Keystore Profile
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCreateForm = !showCreateForm },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add New Keystore Profile", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Icon(
                                    imageVector = if (showCreateForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            if (showCreateForm) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = aliasInput,
                                        onValueChange = { aliasInput = it },
                                        label = { Text("Profile Alias Name (e.g. MyKeystore)") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = keystoreBase64Input,
                                        onValueChange = { keystoreBase64Input = it },
                                        label = { Text("Keystore Base64 String") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = keystorePasswordInput,
                                        onValueChange = { keystorePasswordInput = it },
                                        label = { Text("Keystore Password") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = keyAliasInput,
                                        onValueChange = { keyAliasInput = it },
                                        label = { Text("Key Alias") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = keyPasswordInput,
                                        onValueChange = { keyPasswordInput = it },
                                        label = { Text("Key Password") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            settingsViewModel.saveKeystoreProfile(
                                                alias = aliasInput.trim(),
                                                keystoreBase64 = keystoreBase64Input.trim(),
                                                keystorePassword = keystorePasswordInput.trim(),
                                                keyAlias = keyAliasInput.trim(),
                                                keyPassword = keyPasswordInput.trim()
                                            )
                                            aliasInput = ""
                                            keystoreBase64Input = ""
                                            keystorePasswordInput = ""
                                            keyAliasInput = ""
                                            keyPasswordInput = ""
                                            showCreateForm = false
                                        },
                                        enabled = aliasInput.isNotBlank() && keystoreBase64Input.isNotBlank() && keystorePasswordInput.isNotBlank(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save Keystore Profile", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Raw URL Downloader Management Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Raw URL Downloader",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "Recursively scan a repository folder, generate raw GitHub paths for all underlying files, and compile them into a numbered index list document.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Select Target Repository Dropdown
                            Text(
                                text = "Select Repository",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { downloaderRepoDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedDownloaderRepo?.fullName ?: "Select Target Repository",
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                DropdownMenu(
                                    expanded = downloaderRepoDropdownExpanded,
                                    onDismissRequest = { downloaderRepoDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .fillMaxWidth(0.85f)
                                ) {
                                    repositories.forEach { repo ->
                                        DropdownMenuItem(
                                            text = { Text(repo.fullName, color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                selectedDownloaderRepo = repo
                                                selectedDownloaderDir = ""
                                                downloaderRepoDropdownExpanded = false
                                                settingsViewModel.fetchDirectoriesForRepository(
                                                    owner = repo.owner?.login ?: "",
                                                    repoName = repo.name,
                                                    branch = repo.defaultBranch
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // Select Folder / Directory Dropdown
                            Text(
                                text = "Select Folder / Directory",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { downloaderDirDropdownExpanded = true },
                                    enabled = selectedDownloaderRepo != null,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (selectedDownloaderDir.isEmpty()) "root (All Files)" else "/$selectedDownloaderDir",
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                DropdownMenu(
                                    expanded = downloaderDirDropdownExpanded,
                                    onDismissRequest = { downloaderDirDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .fillMaxWidth(0.85f)
                                ) {
                                    repoDirectories.forEach { dir ->
                                        DropdownMenuItem(
                                            text = { Text(if (dir.isEmpty()) "root" else "/$dir", color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                selectedDownloaderDir = dir
                                                downloaderDirDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Run Raw URL Downloader Action Button
                            Button(
                                onClick = {
                                    selectedDownloaderRepo?.let { repo ->
                                        settingsViewModel.downloadRawUrlsForDirectory(
                                            owner = repo.owner?.login ?: "",
                                            repoName = repo.name,
                                            branch = repo.defaultBranch,
                                            directory = selectedDownloaderDir
                                        )
                                    }
                                },
                                enabled = selectedDownloaderRepo != null && !isDownloadingUrls && !isLoading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download Raw URLs List", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Account & Security Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Account & Security",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )

                            SettingsRow(
                                icon = Icons.Default.Security,
                                title = "Authentication Token",
                                subtitle = if (authViewModel.tokenManager.isDemoMode()) "Demo Account" else "Encrypted Token Stored",
                                onClick = {}
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsRow(
                                icon = Icons.Default.ExitToApp,
                                title = "Log Out",
                                subtitle = "Disconnect current account session",
                                iconTint = GhErrorRed,
                                onClick = { showLogoutDialog = true }
                            )
                        }
                    }
                }

                4 -> {
                    // About & Version Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("About FastGit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text("Version 1.0.0 (Build 100)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                            Text("Built with Kotlin, Coroutines & Jetpack Compose for Android", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to log out of FastGit?", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        authViewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GhErrorRed)
                ) {
                    Text("Log Out", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Modal Process Loading Overlay Dialog
    if (isDownloadingUrls) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Raw URL Downloader",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = downloadStep,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}