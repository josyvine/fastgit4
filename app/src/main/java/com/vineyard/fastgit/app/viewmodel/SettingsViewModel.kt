package com.vineyard.fastgit.app.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.vineyard.fastgit.app.database.AppDatabase
import com.vineyard.fastgit.app.database.KeystoreProfileEntity
import com.vineyard.fastgit.app.models.*
import com.vineyard.fastgit.app.network.RetrofitClient
import com.vineyard.fastgit.app.utils.AppLogger
import com.vineyard.fastgit.app.utils.TokenManager
import com.vineyard.fastgit.app.utils.DownloadUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val tokenManager = TokenManager(application)
    private val database = AppDatabase.getInstance(application)
    private val keystoreDao = database.keystoreProfileDao()

    // Standard Preferences States
    private val _themeMode = MutableStateFlow("System")
    val themeMode: StateFlow<String> = _themeMode

    private val _cacheSize = MutableStateFlow("4.2 MB")
    val cacheSize: StateFlow<String> = _cacheSize

    // Propagation Feature States
    private val _repositories = MutableStateFlow<List<Repository>>(emptyList())
    val repositories: StateFlow<List<Repository>> = _repositories

    private val _savedAliases = MutableStateFlow<List<String>>(emptyList())
    val savedAliases: StateFlow<List<String>> = _savedAliases

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Dynamic Repository Directories States
    private val _repoDirectories = MutableStateFlow<List<String>>(emptyList())
    val repoDirectories: StateFlow<List<String>> = _repoDirectories

    // Raw URL Downloader Progress States
    private val _isDownloadingUrls = MutableStateFlow(false)
    val isDownloadingUrls: StateFlow<Boolean> = _isDownloadingUrls

    private val _downloadStep = MutableStateFlow("")
    val downloadStep: StateFlow<String> = _downloadStep

    init {
        _themeMode.value = "System"
        observeSavedProfiles()
        loadUserRepositories()
    }

    // Standard Preferences Logic
    fun setTheme(theme: String) {
        _themeMode.value = theme
        AppLogger.i("Settings", "App theme set to: $theme")
    }

    fun clearCache() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                AppDatabase.getInstance(getApplication()).cacheDao().clearAllCache()
                _cacheSize.value = "0 KB"
                _statusMessage.value = "Cache cleared successfully!"
                AppLogger.s("Settings", "Database cache cleared successfully.")
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to clear database cache: ${e.message}", e)
                _statusMessage.value = "Failed to clear cache: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Database Keystore Profiles Storage Logic
    private fun observeSavedProfiles() {
        viewModelScope.launch {
            keystoreDao.getAllProfilesFlow().collect { profiles ->
                _savedAliases.value = profiles.map { it.alias }
            }
        }
    }

    fun saveKeystoreProfile(
        alias: String,
        keystoreBase64: String,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String
    ) {
        if (alias.isBlank()) {
            _statusMessage.value = "Profile alias is required"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = KeystoreProfileEntity(
                    alias = alias,
                    keystoreBase64 = keystoreBase64,
                    keystorePassword = keystorePassword,
                    keyAlias = keyAlias,
                    keyPassword = keyPassword
                )
                keystoreDao.insertProfile(entity)
                
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Keystore profile '$alias' saved to database!"
                    AppLogger.s("Settings", "Saved keystore profile alias to database: '$alias'")
                }
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to save keystore profile to database: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to save profile: ${e.message}"
                }
            }
        }
    }

    fun deleteKeystoreProfile(alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                keystoreDao.deleteProfile(alias)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Keystore profile '$alias' deleted!"
                    AppLogger.i("Settings", "Deleted keystore profile alias: '$alias'")
                }
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to delete keystore profile: ${e.message}", e)
            }
        }
    }

    fun loadUserRepositories() {
        if (tokenManager.isDemoMode()) {
            _repositories.value = listOf(
                Repository(id = 1, name = "FastGit-Android", fullName = "developer_android/FastGit-Android", defaultBranch = "main"),
                Repository(id = 2, name = "FastGit-Backend", fullName = "developer_android/FastGit-Backend", defaultBranch = "main")
            )
            return
        }
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                val repos = api.getUserRepositories()
                _repositories.value = repos
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to fetch repositories list: ${e.message}", e)
            }
        }
    }

    // Fetches the repository recursive tree structure to filter all folders/directories
    fun fetchDirectoriesForRepository(owner: String, repoName: String, branch: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _repoDirectories.value = emptyList()
            try {
                if (tokenManager.isDemoMode()) {
                    delay(1000)
                    _repoDirectories.value = listOf(
                        "",
                        "app",
                        "app/src",
                        "app/src/main",
                        "app/src/main/java",
                        "app/src/main/res",
                        "database",
                        "models",
                        "network",
                        "ui",
                        "utils",
                        "viewmodel"
                    )
                    AppLogger.s("Settings", "Simulated directory listing in Demo Mode")
                } else {
                    val targetBranch = branch.ifBlank { "main" }
                    val api = RetrofitClient.getService(tokenManager)
                    AppLogger.i("Settings", "Fetching recursive tree for folders: $owner/$repoName ($targetBranch)")
                    val response = api.getRecursiveTree(owner, repoName, targetBranch)
                    
                    // Filter out only directories (type == "tree")
                    val dirs = response.tree
                        .filter { it.type == "tree" }
                        .map { it.path }
                        .sorted()
                    
                    // Add empty string to indicate "root" of the repository
                    _repoDirectories.value = listOf("") + dirs
                    AppLogger.s("Settings", "Fetched ${dirs.size} folders successfully.")
                }
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to fetch directories list: ${e.message}", e)
                _statusMessage.value = "Failed to fetch folders: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Recursively scans the targeted folder, builds the GitHub raw URLs, and downloads them in a numbered text file
    fun downloadRawUrlsForDirectory(owner: String, repoName: String, branch: String, directory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isDownloadingUrls.value = true
                _downloadStep.value = "Contacting GitHub API..."
            }

            try {
                val targetBranch = branch.ifBlank { "main" }
                val urlsList = mutableListOf<String>()

                if (tokenManager.isDemoMode()) {
                    withContext(Dispatchers.Main) {
                        _downloadStep.value = "Scanning folder hierarchy (Demo Mode)..."
                    }
                    delay(1500)
                    
                    val prefix = if (directory.isEmpty()) "" else "$directory/"
                    val mockPaths = listOf(
                        "${prefix}MainActivity.kt",
                        "${prefix}DatabaseComponents.kt",
                        "${prefix}Models.kt",
                        "${prefix}GitHubApiService.kt",
                        "${prefix}RetrofitClient.kt",
                        "${prefix}MainScreen.kt"
                    )
                    
                    mockPaths.forEach { path ->
                        val cleanPath = path.removePrefix("/")
                        val rawUrl = "https://raw.githubusercontent.com/$owner/$repoName/$targetBranch/$cleanPath"
                        urlsList.add(rawUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _downloadStep.value = "Fetching recursive repository tree..."
                    }
                    val api = RetrofitClient.getService(tokenManager)
                    val response = api.getRecursiveTree(owner, repoName, targetBranch)

                    withContext(Dispatchers.Main) {
                        _downloadStep.value = "Scanning files inside directory: /${directory.ifEmpty { "root" }}..."
                    }

                    // Filter all file nodes (type == "blob") located inside the selected directory tree path
                    val matchingFiles = response.tree.filter { entry ->
                        entry.type == "blob" && (directory.isEmpty() || entry.path.startsWith("$directory/"))
                    }

                    withContext(Dispatchers.Main) {
                        _downloadStep.value = "Generating raw path structures for ${matchingFiles.size} files..."
                    }

                    matchingFiles.forEach { entry ->
                        val rawUrl = "https://raw.githubusercontent.com/$owner/$repoName/$targetBranch/${entry.path}"
                        urlsList.add(rawUrl)
                    }
                }

                if (urlsList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "No files found in folder /${directory.ifEmpty { "root" }}."
                        _isDownloadingUrls.value = false
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _downloadStep.value = "Assembling raw URLs text database..."
                }

                // Assemble numbered string list
                val contentBuilder = StringBuilder()
                urlsList.forEachIndexed { index, url ->
                    contentBuilder.append("${index + 1}. $url\n")
                }

                withContext(Dispatchers.Main) {
                    _downloadStep.value = "Saving file to device directory..."
                }

                val safeDirName = directory.replace('/', '_').ifEmpty { "root" }
                val exportFileName = "raw_urls_${repoName}_$safeDirName.txt"

                val savedFile = DownloadUtils.saveTextToDownloads(
                    context = getApplication(),
                    subFolder = "Urls",
                    fileName = exportFileName,
                    content = contentBuilder.toString()
                )

                withContext(Dispatchers.Main) {
                    if (savedFile != null) {
                        _statusMessage.value = "URLs saved successfully to: Downloads/FastGit/Urls/$exportFileName"
                        AppLogger.s("Settings", "Raw URLs exported successfully: $exportFileName")
                    } else {
                        _statusMessage.value = "Local system failed to write exports file."
                    }
                }

            } catch (e: Exception) {
                AppLogger.e("Settings", "Raw URL generation sequence crashed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Export process failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isDownloadingUrls.value = false
                    _downloadStep.value = ""
                }
            }
        }
    }

    // Automated Secret Propagation Logic
    fun propagateKeystoreToRepository(
        targetRepoOwner: String,
        targetRepoName: String,
        profileAlias: String
    ) {
        if (profileAlias.isBlank()) {
            _statusMessage.value = "Please select a valid keystore profile alias"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _statusMessage.value = "Retrieving profile details and propagating credentials..."
            }

            try {
                val profile = keystoreDao.getProfile(profileAlias)
                if (profile == null) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Selected keystore profile '$profileAlias' could not be found in database"
                        _isLoading.value = false
                    }
                    return@launch
                }

                val secretsMap = mapOf(
                    "KEYSTORE_BASE64" to profile.keystoreBase64,
                    "KEYSTORE_PASSWORD" to profile.keystorePassword,
                    "KEY_ALIAS" to profile.keyAlias,
                    "KEY_PASSWORD" to profile.keyPassword
                )

                if (tokenManager.isDemoMode()) {
                    delay(1500)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Successfully propagated secrets to $targetRepoOwner/$targetRepoName (Simulated)!"
                        _isLoading.value = false
                    }
                    AppLogger.s("Settings", "Simulated propagation of 4 secrets to $targetRepoOwner/$targetRepoName successfully.")
                    return@launch
                }

                val api = RetrofitClient.getService(tokenManager)
                AppLogger.i("Settings", "Retrieving Actions public key for repository: $targetRepoOwner/$targetRepoName")
                
                // 1. Fetch Repository's Public Key from GitHub API
                val publicKeyResponse = api.getActionsPublicKey(targetRepoOwner, targetRepoName)
                val publicKeyBase64 = publicKeyResponse.key
                val keyId = publicKeyResponse.key_id

                AppLogger.s("Settings", "Retrieved public key ($keyId) successfully. Encrypting secret payload...")

                // 2. Encrypt and upload each secret sequentially
                var successfulCount = 0
                for ((secretName, secretValue) in secretsMap) {
                    if (secretValue.isEmpty()) {
                        AppLogger.i("Settings", "Skipping empty secret '$secretName'")
                        continue
                    }

                    AppLogger.i("Settings", "Encrypting secret '$secretName' using Libsodium sealed box...")
                    val encryptedValue = encryptWithPublicKey(secretValue, publicKeyBase64)
                    
                    val request = CreateSecretRequest(
                        encrypted_value = encryptedValue,
                        key_id = keyId
                    )

                    AppLogger.i("Settings", "Uploading encrypted secret '$secretName' to GitHub...")
                    val response = api.createOrUpdateActionsSecret(
                        owner = targetRepoOwner,
                        repo = targetRepoName,
                        secretName = secretName,
                        request = request
                    )

                    if (response.isSuccessful) {
                        successfulCount++
                        AppLogger.s("Settings", "Uploaded secret '$secretName' successfully!")
                    } else {
                        val err = response.errorBody()?.string() ?: response.message()
                        AppLogger.e("Settings", "Failed to upload secret '$secretName': $err")
                    }
                }

                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Propagated $successfulCount/4 secrets to $targetRepoName successfully!"
                    AppLogger.s("Settings", "Credential propagation complete. Saved $successfulCount secrets on GitHub.")
                }

            } catch (e: Exception) {
                AppLogger.e("Settings", "Secret propagation pipeline failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Propagation failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Corrected, robust implementation of NaCl Sealed Box encryption using standard LazySodium.
     * Encrypts the raw secret string against the repository's Base64 public key.
     */
    private fun encryptWithPublicKey(secret: String, publicKeyBase64: String): String {
        return try {
            // 1. Base64 decode the public key retrieved from the GitHub API
            val recipientPublicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)

            // 2. Initialize the standard LazySodium engine with the Android backend wrapper
            val lazySodium = LazySodiumAndroid(SodiumAndroid())

            // 3. Encrypt the secret using standard Libsodium crypto_box_seal_easy (Sealed Box)
            // This method automatically handles ephemeral keypair generation, BLAKE2b nonce derivation,
            // XSalsa20-Poly1305 encryption, and outputs the ciphertext as a hex-encoded string.
            val encryptedHex = lazySodium.cryptoBoxSealEasy(secret, Key.fromBytes(recipientPublicKeyBytes))
            
            // 4. Parse the hexadecimal string back to standard raw bytes using the correct method
            val cipherBytes = lazySodium.sodiumHex2Bin(encryptedHex)
            
            // 5. Encode the final ciphertext bytes back to a Base64 string to match the format required by GitHub
            Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Libsodium Sealed Box encryption failed: ${e.message}", e)
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}