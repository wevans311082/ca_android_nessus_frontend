package com.wevans.caandroidnessusfrontend.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wevans.caandroidnessusfrontend.MainActivity
import com.wevans.caandroidnessusfrontend.data.*
import kotlinx.coroutines.delay
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class LocalReport(
    val filePath: String,
    val displayName: String,
    val createdAt: Long
)

data class NessusUiState(
    val settings: NessusSettings = NessusSettings(),
    val scans: List<ScanSummary> = emptyList(),
    val selectedScan: ScanSummary? = null,
    val selectedScanDetail: NessusScanDetailResponse? = null,
    val hostVulnerabilities: List<ScanVulnerability>? = null,
    val selectedPlugin: List<PluginAttribute> = emptyList(),
    val groups: List<NessusGroup> = emptyList(),
    val agentGroups: List<NessusAgentGroup> = emptyList(),
    val groupAgents: List<NessusAgent> = emptyList(),
    val agents: List<NessusAgent> = emptyList(),
    val selectedAgent: NessusAgent? = null,
    val loading: Boolean = false,
    val isDownloadingReport: Boolean = false,
    val message: String? = null,
    val downloadedFilePath: String? = null,
    val localReports: List<LocalReport> = emptyList(),
    val scanners: List<Scanner> = emptyList(),
    val selectedScannerId: String = "1",
    val connectionStatus: String? = null,
    val lastConnected: Long? = null,
    val scanTemplates: List<ScanTemplate> = emptyList(),
    val isCreatingScan: Boolean = false,
    val requireBiometric: Boolean = false,
    val isUnlocked: Boolean = false
)

class NessusViewModel(
    private val settingsStore: SettingsStore,
    private val repositoryFactory: (NessusSettings) -> NessusRepository,
    private val applicationContext: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(NessusUiState())
    val uiState: StateFlow<NessusUiState> = _uiState.asStateFlow()

    private fun getReportsDir(): File {
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(publicDocs, "CyberAsk Reports").apply { mkdirs() }
    }

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { 
                    it.copy(
                        settings = settings,
                        requireBiometric = settings.requireBiometric,
                        isUnlocked = !settings.requireBiometric || it.isUnlocked
                    )
                }
            }
        }
    }

    fun saveSettings(baseUrl: String, accessKey: String, secretKey: String, scannerId: String? = null) {
        viewModelScope.launch {
            val current = uiState.value.settings
            val newScanner = scannerId?.trim()?.ifBlank { "1" } ?: current.scannerId
            settingsStore.save(NessusSettings(baseUrl.trim(), accessKey.trim(), secretKey.trim(), newScanner))
            _uiState.update { it.copy(message = "Settings saved", selectedScannerId = newScanner) }
        }
    }

    fun testConnection() = runManaged("Connection failed") {
        val status = repository().testConnection()
        val now = System.currentTimeMillis()
        if (status.status == "ready") {
            _uiState.update { 
                it.copy(
                    message = "Connection successful!",
                    connectionStatus = "Connected",
                    lastConnected = now
                ) 
            }
        } else {
            _uiState.update { 
                it.copy(
                    message = "Connection failed: Server not ready (${status.status})",
                    connectionStatus = "Failed",
                    lastConnected = now
                ) 
            }
        }
    }

    fun loadScanners() = runManaged("Could not load scanners") {
        val list = repository().listScanners()
        val currentSettings = uiState.value.settings
        _uiState.update { it.copy(scanners = list, selectedScannerId = currentSettings.scannerId) }
    }

    fun selectScanner(scannerId: String) {
        viewModelScope.launch {
            val s = uiState.value.settings
            settingsStore.save(s.copy(scannerId = scannerId))
            _uiState.update { 
                it.copy(
                    selectedScannerId = scannerId,
                    message = "Scanner selected: $scannerId"
                ) 
            }
            // Refresh agent data with new scanner
            loadAgentGroups()
            loadAgents()
        }
    }

    fun downloadReport(scanId: Int, chapters: String, scanName: String? = null, format: String = "pdf") = viewModelScope.launch {
        val settings = uiState.value.settings
        val delayMs = settings.pollingIntervalMs
        val maxAttempts = (settings.exportTimeoutSeconds * 1000 / delayMs).toInt().coerceAtLeast(10)

        _uiState.update { it.copy(isDownloadingReport = true, message = "Requesting report generation...") }
        try {
            val fileId = repository().exportScan(scanId, chapters, format)
            var status = "loading"
            var attempts = 0

            while (status != "ready" && attempts < maxAttempts) {
                delay(delayMs)
                attempts++
                status = repository().getExportStatus(scanId, fileId)
                _uiState.update { it.copy(message = "Generating report: $status...") }

                if (status.equals("error", ignoreCase = true) || status.equals("failed", ignoreCase = true)) {
                    throw Exception("Report generation failed on server (status: $status)")
                }
            }

            if (status != "ready") {
                throw Exception("Report generation timed out after ~${settings.exportTimeoutSeconds} seconds")
            }

            val responseBody = repository().downloadScan(scanId, fileId)

            // Store in public Documents/CyberAsk Reports
            val reportsDir = getReportsDir()
            val safeName = (scanName?.take(50)?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "scan-$scanId")
            val timestamp = System.currentTimeMillis()
            val ext = if (format == "pdf") "pdf" else format
            val fileName = "${safeName}_$timestamp.$ext"
            val file = File(reportsDir, fileName)

            responseBody.byteStream().use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }

            // Add to local history
            val displayName = scanName ?: "Scan #$scanId"
            addLocalReport(file.absolutePath, displayName, timestamp)

            _uiState.update { 
                it.copy(
                    downloadedFilePath = file.absolutePath, 
                    message = "Report saved locally. Open Reports to view or share."
                ) 
            }
            showReportNotification(scanName ?: "Scan #$scanId", file.absolutePath)
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Failed: ${e.message}") }
        } finally {
            _uiState.update { it.copy(isDownloadingReport = false) }
        }
    }

    private fun addLocalReport(path: String, displayName: String, timestamp: Long) {
        val newReport = LocalReport(path, displayName, timestamp)
        val current = _uiState.value.localReports
        // Avoid duplicates
        val updated = (listOf(newReport) + current.filter { it.filePath != path })
            .sortedByDescending { it.createdAt }
        _uiState.update { it.copy(localReports = updated) }
    }

    fun loadLocalReports() {
        val reportsDir = getReportsDir()
        if (!reportsDir.exists()) return

        val files = reportsDir.listFiles { f -> f.isFile && f.name.endsWith(".pdf", true) } ?: return
        val reports = files.map { file ->
            // Try to make a nice name from filename
            val base = file.nameWithoutExtension.substringBeforeLast('_')
            val display = if (base.isNotBlank()) base.replace('_', ' ') else file.name
            LocalReport(file.absolutePath, display, file.lastModified())
        }.sortedByDescending { it.createdAt }

        _uiState.update { it.copy(localReports = reports) }
    }

    fun openReport(path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(
                applicationContext,
                applicationContext.packageName + ".provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            applicationContext.startActivity(Intent.createChooser(intent, "Open PDF"))
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Could not open PDF: ${e.message}") }
        }
    }

    fun shareReport(path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(
                applicationContext,
                applicationContext.packageName + ".provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            applicationContext.startActivity(Intent.createChooser(intent, "Share Report"))
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Could not share PDF: ${e.message}") }
        }
    }

    fun deleteLocalReport(path: String) {
        try {
            File(path).delete()
            val updated = _uiState.value.localReports.filter { it.filePath != path }
            _uiState.update { it.copy(localReports = updated, message = "Report deleted") }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Failed to delete: ${e.message}") }
        }
    }

    fun renameLocalReport(oldPath: String, newDisplayName: String) {
        try {
            val oldFile = File(oldPath)
            if (!oldFile.exists()) return

            val dir = oldFile.parentFile ?: return
            val extension = oldFile.extension
            val timestamp = oldFile.nameWithoutExtension.substringAfterLast('_', System.currentTimeMillis().toString())
            val safeNewName = newDisplayName.take(50).replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val newFileName = "${safeNewName}_$timestamp.$extension"
            val newFile = File(dir, newFileName)

            if (oldFile.renameTo(newFile)) {
                val updated = _uiState.value.localReports.map { report ->
                    if (report.filePath == oldPath) {
                        report.copy(filePath = newFile.absolutePath, displayName = newDisplayName)
                    } else report
                }
                _uiState.update { it.copy(localReports = updated, message = "Report renamed") }
            } else {
                _uiState.update { it.copy(message = "Failed to rename file") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Rename failed: ${e.message}") }
        }
    }

    fun clearDownloadedFile() {
        _uiState.update { it.copy(downloadedFilePath = null) }
    }

    fun setRequireBiometric(require: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.settings
            val updated = current.copy(requireBiometric = require)
            settingsStore.save(updated)
            _uiState.update { it.copy(requireBiometric = require) }
        }
    }

    fun unlockApp() {
        _uiState.update { it.copy(isUnlocked = true) }
    }

    fun lockApp() {
        _uiState.update { it.copy(isUnlocked = false) }
    }

    fun resetCreatingScan() {
        _uiState.update { it.copy(isCreatingScan = false) }
    }

    private fun showReportNotification(scanName: String, path: String) {
        val channelId = "report_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Scan Reports", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Report Ready")
            .setContentText("Scan report for $scanName is ready")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(scanName.hashCode(), notification)
    }

    fun loadScans() = runManaged("Could not load scans") {
        val scans = repository().listScans()
        _uiState.update { it.copy(scans = scans) }
    }

    fun loadScanDetails(scan: ScanSummary, historyId: Int? = null) = runManaged("Could not load scan details") {
        val detail = repository().getScan(scan.id, historyId)
        _uiState.update { currentState ->
            val historyToKeep = if (historyId != null && detail.history.isNullOrEmpty()) {
                currentState.selectedScanDetail?.history
            } else {
                detail.history
            }
            currentState.copy(
                selectedScan = scan, 
                selectedScanDetail = detail.copy(history = historyToKeep), 
                selectedPlugin = emptyList(),
                hostVulnerabilities = null
            )
        }
    }
    
    fun loadHostVulnerabilities(scanId: Int, hostId: Int?, historyId: Int?) {
        if (hostId == null) {
            _uiState.update { it.copy(hostVulnerabilities = null) }
            return
        }
        runManaged("Could not load host details") {
            val response = repository().getScanHost(scanId, hostId, historyId)
            _uiState.update { it.copy(hostVulnerabilities = response.vulnerabilities) }
        }
    }

    fun loadPlugin(pluginId: Int) {
        if (pluginId == 0) {
            _uiState.update { it.copy(selectedPlugin = emptyList()) }
            return
        }
        runManaged("Could not load plugin") {
            val plugin = repository().getPlugin(pluginId)
            _uiState.update { it.copy(selectedPlugin = plugin) }
        }
    }

    fun startScan(scanId: Int) = runManaged("Could not start scan") {
        repository().startScan(scanId)
        loadScans()
    }

    fun stopScan(scanId: Int) = runManaged("Could not stop scan") {
        repository().stopScan(scanId)
        loadScans()
    }

    fun pauseScan(scanId: Int) = runManaged("Could not pause scan") {
        repository().pauseScan(scanId)
        loadScans()
    }

    fun resumeScan(scanId: Int) = runManaged("Could not resume scan") {
        repository().resumeScan(scanId)
        loadScans()
    }

    fun deleteScan(scanId: Int) = runManaged("Could not delete scan") {
        repository().deleteScan(scanId)
        loadScans()
    }

    // ===== Scan Creation =====
    fun loadScanTemplates() = runManaged("Could not load scan templates") {
        val templates = repository().listScanTemplates()
        _uiState.update { it.copy(scanTemplates = templates) }
    }

    fun createScanFromTemplate(
        templateUuid: String,
        name: String,
        description: String = "",
        targets: String? = null,
        agentGroupId: String? = null,
        scannerId: String? = null
    ) = runManaged("Could not create scan") {
        _uiState.update { it.copy(isCreatingScan = true) }

        try {
            val effectiveScanner = scannerId ?: uiState.value.selectedScannerId.ifBlank { "1" }
            val settings = CreateScanSettings(
                name = name.trim(),
                description = description.trim().ifBlank { null },
                targets = targets?.trim()?.ifBlank { null },
                agentGroupId = agentGroupId,
                scannerId = effectiveScanner.takeIf { it.isNotBlank() && it != "null" },
                launchNow = false
            )
            val request = CreateScanRequest(uuid = templateUuid, settings = settings)

            val response = repository().createScan(request)

            // Refresh the scans list after creation
            loadScans()

            _uiState.update { it.copy(isCreatingScan = false, message = "Scan \"${name}\" created successfully") }
        } catch (e: Exception) {
            _uiState.update { it.copy(isCreatingScan = false) }
            throw e
        }
    }

    fun loadGroups() = runManaged("Could not load groups") {
        val groups = repository().listGroups()
        _uiState.update { it.copy(groups = groups) }
    }

    fun createGroup(name: String) = runManaged("Could not create group") {
        repository().createGroup(name)
        loadGroups()
    }

    fun deleteGroup(groupId: String) = runManaged("Could not delete group") {
        repository().deleteGroup(groupId)
        loadGroups()
    }

    fun loadAgentGroups() = runManaged("Could not load agent groups") {
        val groups = repository().listAgentGroups()
        _uiState.update { it.copy(agentGroups = groups) }
    }

    fun createAgentGroup(name: String) = runManaged("Could not create agent group") {
        repository().createAgentGroup(name)
        loadAgentGroups()
    }

    fun deleteAgentGroup(groupId: String) = runManaged("Could not delete agent group") {
        repository().deleteAgentGroup(groupId)
        loadAgentGroups()
    }

    fun loadGroupAgents(groupId: String) = runManaged("Could not load agents in group") {
        val agents = repository().listAgentsInGroup(groupId)
        _uiState.update { it.copy(groupAgents = agents) }
    }

    fun addAgentToGroup(groupId: String, agentId: Int) = runManaged("Could not add agent to group") {
        repository().addAgentToGroup(groupId, agentId)
        loadGroupAgents(groupId)
    }

    fun removeAgentFromGroup(groupId: String, agentId: Int) = runManaged("Could not remove agent from group (API key may need 'Scan Manager' role)") {
        repository().removeAgentFromGroup(groupId, agentId)
        loadGroupAgents(groupId)
    }

    fun loadAgents() = runManaged("Could not load agents") {
        val agents = repository().listAgents()
        _uiState.update { it.copy(agents = agents) }
    }

    fun unlinkAgent(agentId: Int) = runManaged("Could not unlink agent (check that your API key has Scan Manager [40] permissions)") {
        repository().unlinkAgent(agentId)
        loadAgents()
    }
    
    fun selectAgent(agent: NessusAgent?) {
        _uiState.update { it.copy(selectedAgent = agent) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun repository(): NessusRepository = repositoryFactory(uiState.value.settings)

    private fun runManaged(defaultError: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try { block() } catch (e: Exception) {
                val msg = when (e) {
                    is HttpException -> {
                        val code = e.code()
                        val detail = e.message()
                        val body = try { e.response()?.errorBody()?.string()?.take(200) ?: "" } catch (_: Exception) { "" }
                        val extra = if (body.isNotBlank()) "\nServer response: $body" else ""
                        when (code) {
                            401 -> "$defaultError (HTTP 401 Unauthorized — check your Access Key and Secret Key)"
                            403 -> "$defaultError (HTTP 403 Forbidden — insufficient permissions (needs Scan Manager role) or wrong scanner ID)"
                            404 -> "$defaultError (HTTP 404 Not Found — check scanner ID or endpoint)"
                            429 -> "$defaultError (HTTP 429 Rate limited — try again later)"
                            502 -> "$defaultError (HTTP 502 Bad Gateway — the Nessus server could not process the request. Common causes: invalid/missing settings for the chosen template, wrong scanner ID, or server/proxy issue. Try a different template or check server logs.)$extra"
                            else -> "$defaultError (HTTP $code: $detail)$extra"
                        }
                    }
                    else -> e.message ?: defaultError
                }
                _uiState.update { it.copy(message = msg, connectionStatus = "Error") }
            } finally { _uiState.update { it.copy(loading = false) } }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val store = SettingsStore(context.applicationContext)
                return NessusViewModel(store, { settings ->
                    NessusRepository(
                        settingsProvider = { settings },
                        apiFactory = NessusApiFactory {
                            NessusAuth(accessKey = settings.accessKey, secretKey = settings.secretKey)
                        }
                    )
                }, context.applicationContext) as T
            }
        }
    }
}
