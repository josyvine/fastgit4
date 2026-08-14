package com.vineyard.fastgit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.fastgit.app.models.FileItem
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.utils.SyntaxHighlighter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    fileItem: FileItem,
    initialContent: String,
    onBack: () -> Unit,
    onSaveAndCommit: (updatedContent: String, commitMessage: String) -> Unit,
    onDownloadClick: (content: String) -> Unit
) {
    val context = LocalContext.current

    // State variables for editor contents
    var codeText by remember(initialContent) { mutableStateOf(initialContent) }
    var undoStack by remember(initialContent) { mutableStateOf(listOf(initialContent)) }
    var redoStack by remember(initialContent) { mutableStateOf(listOf<String>()) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showSearchReplaceDialog by remember { mutableStateOf(false) }

    // Search & Replace Dialog States
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }

    // Track the last state pushed to the undo stack to optimize memory allocations
    var lastPushedText by remember(initialContent) { mutableStateOf(initialContent) }

    val lines = codeText.split("\n")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileItem.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = fileItem.path,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Collapsible Actions Dropdown (Search & Replace, Copy, Paste, Cut, Delete)
                    Box {
                        IconButton(onClick = { showMenuDropdown = true }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Editor Action Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Search & Replace", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showMenuDropdown = false
                                    showSearchReplaceDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FindReplace, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showMenuDropdown = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Code", codeText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Paste", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showMenuDropdown = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                                        if (pastedText.isNotEmpty()) {
                                            val oldText = codeText
                                            if (oldText != lastPushedText) {
                                                undoStack = undoStack + oldText
                                            }
                                            codeText = pastedText
                                            undoStack = undoStack + pastedText
                                            lastPushedText = pastedText
                                            redoStack = emptyList()
                                            Toast.makeText(context, "Pasted clipboard content!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = GhSuccessGreen)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cut", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showMenuDropdown = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Code", codeText)
                                    clipboard.setPrimaryClip(clip)
                                    
                                    val oldText = codeText
                                    if (oldText.isNotEmpty()) {
                                        if (oldText != lastPushedText) {
                                            undoStack = undoStack + oldText
                                        }
                                        codeText = ""
                                        undoStack = undoStack + ""
                                        lastPushedText = ""
                                        redoStack = emptyList()
                                        Toast.makeText(context, "Cut code to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color.Red) },
                                onClick = {
                                    showMenuDropdown = false
                                    val oldText = codeText
                                    if (oldText.isNotEmpty()) {
                                        if (oldText != lastPushedText) {
                                            undoStack = undoStack + oldText
                                        }
                                        codeText = ""
                                        undoStack = undoStack + ""
                                        lastPushedText = ""
                                        redoStack = emptyList()
                                        Toast.makeText(context, "Cleared editor workspace!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                }
                            )
                        }
                    }

                    // Download File Button
                    IconButton(
                        onClick = {
                            onDownloadClick(codeText)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download File",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Undo Action
                    IconButton(
                        onClick = {
                            if (undoStack.size > 1) {
                                val current = undoStack.last()
                                redoStack = redoStack + current
                                val prev = undoStack[undoStack.size - 2]
                                undoStack = undoStack.dropLast(1)
                                codeText = prev
                                lastPushedText = prev
                            }
                        },
                        enabled = undoStack.size > 1
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.size > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    // Redo Action
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                undoStack = undoStack + next
                                codeText = next
                                lastPushedText = next
                            }
                        },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    // Save & Commit Button
                    IconButton(onClick = { showCommitDialog = true }) {
                        Icon(Icons.Default.Check, contentDescription = "Commit Changes", tint = GhSuccessGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
            ) {
                // Line Numbers Column
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lines.size) {
                        Text(
                            text = "$i",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Editor Code Area with High-Performance Syntax Highlighting
                BasicTextField(
                    value = codeText,
                    onValueChange = { newText ->
                        codeText = newText
                        val delta = abs(newText.length - lastPushedText.length)

                        // Only write to undo history stack during major adjustments or word boundaries
                        if (delta > 1 || (newText.isNotEmpty() && (newText.last() == ' ' || newText.last() == '\n'))) {
                            if (newText != lastPushedText) {
                                undoStack = undoStack + newText
                                lastPushedText = newText
                                redoStack = emptyList()
                            }
                        }
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    visualTransformation = {
                        androidx.compose.ui.text.input.TransformedText(
                            SyntaxHighlighter.highlight(it.text, fileItem.name),
                            androidx.compose.ui.text.input.OffsetMapping.Identity
                        )
                    }
                )
            }
        }
    }

    // Search & Replace Dialog
    if (showSearchReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showSearchReplaceDialog = false },
            title = {
                Text(
                    text = "Search & Replace",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search text:") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with:") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = GhSuccessGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedLabelColor = GhSuccessGreen,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Modifiers: Case Sensitive & Regular Expression
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isCaseSensitive,
                            onCheckedChange = { isCaseSensitive = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                checkmarkColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Text(
                            text = "Case sensitive",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isRegex,
                            onCheckedChange = { isRegex = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                checkmarkColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Text(
                            text = "Regular expression",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // REPLACE ALL Button
                    TextButton(
                        onClick = {
                            if (searchText.isEmpty()) {
                                Toast.makeText(context, "Please enter search text", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            try {
                                val matchCount: Int
                                val updated: String

                                if (isRegex) {
                                    val regexOptions = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                    val regex = Regex(searchText, regexOptions)
                                    matchCount = regex.findAll(codeText).count()
                                    updated = codeText.replace(regex, replaceText)
                                } else {
                                    val regexOptions = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                    val regex = Regex(Regex.escape(searchText), regexOptions)
                                    matchCount = regex.findAll(codeText).count()
                                    updated = codeText.replace(searchText, replaceText, ignoreCase = !isCaseSensitive)
                                }

                                if (matchCount > 0) {
                                    val old = codeText
                                    if (old != lastPushedText) {
                                        undoStack = undoStack + old
                                    }
                                    codeText = updated
                                    undoStack = undoStack + updated
                                    lastPushedText = updated
                                    redoStack = emptyList()
                                    Toast.makeText(context, "$matchCount match(es) of \"$searchText\" were replaced with \"$replaceText\".", Toast.LENGTH_SHORT).show()
                                    showSearchReplaceDialog = false
                                } else {
                                    Toast.makeText(context, "No matches found for \"$searchText\".", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("REPLACE ALL", color = GhSuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // REPLACE Single Match Button
                    TextButton(
                        onClick = {
                            if (searchText.isEmpty()) {
                                Toast.makeText(context, "Please enter search text", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            try {
                                val updated: String
                                var replaced = false

                                if (isRegex) {
                                    val regexOptions = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                    val regex = Regex(searchText, regexOptions)
                                    val match = regex.find(codeText)
                                    if (match != null) {
                                        updated = codeText.replaceRange(match.range, replaceText)
                                        replaced = true
                                    } else {
                                        updated = codeText
                                    }
                                } else {
                                    val index = codeText.indexOf(searchText, ignoreCase = !isCaseSensitive)
                                    if (index >= 0) {
                                        updated = codeText.substring(0, index) + replaceText + codeText.substring(index + searchText.length)
                                        replaced = true
                                    } else {
                                        updated = codeText
                                    }
                                }

                                if (replaced) {
                                    val old = codeText
                                    if (old != lastPushedText) {
                                        undoStack = undoStack + old
                                    }
                                    codeText = updated
                                    undoStack = undoStack + updated
                                    lastPushedText = updated
                                    redoStack = emptyList()
                                    Toast.makeText(context, "Replaced 1 occurrence of \"$searchText\".", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No matches found for \"$searchText\".", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("REPLACE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // SEARCH / FIND Button
                    TextButton(
                        onClick = {
                            if (searchText.isEmpty()) {
                                Toast.makeText(context, "Please enter search text", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            try {
                                val matchCount = if (isRegex) {
                                    val regexOptions = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                    Regex(searchText, regexOptions).findAll(codeText).count()
                                } else {
                                    val regexOptions = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                    Regex(Regex.escape(searchText), regexOptions).findAll(codeText).count()
                                }

                                Toast.makeText(context, "Found $matchCount match(es) for \"$searchText\".", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Invalid regex pattern: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("SEARCH", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchReplaceDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Commit Message Entry Dialog
    if (showCommitDialog) {
        var commitMsg by remember { mutableStateOf("Update ${fileItem.name}") }

        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit Changes", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a commit message for this update:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = commitMsg,
                        onValueChange = { commitMsg = it },
                        label = { Text("Commit Message") },
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
                        showCommitDialog = false
                        if (codeText != lastPushedText) {
                            undoStack = undoStack + codeText
                            lastPushedText = codeText
                        }
                        onSaveAndCommit(codeText, commitMsg)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)
                ) {
                    Text("Commit & Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}