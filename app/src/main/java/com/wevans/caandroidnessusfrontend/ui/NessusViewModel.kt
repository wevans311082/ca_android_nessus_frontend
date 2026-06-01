package com.wevans.caandroidnessusfrontend.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wevans.caandroidnessusfrontend.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
    val downloadedFilePath: String? = null
)

class NessusViewModel(
    private val settingsStore: SettingsStore,
    private val repositoryFactory: (NessusSettings) -> NessusRepository,
    private val applicationContext: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(NessusUiState())
    val uiState: StateFlow<NessusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun saveSettings(baseUrl: String, accessKey: String, secretKey: String) {
        viewModelScope.launch {
            settingsStore.save(NessusSettings(baseUrl, accessKey, secretKey))
            _uiState.update { it.copy(message = "Settings saved") }
        }
    }

    fun testConnection() = runManaged("Connection failed") {
        val status = repository().testConnection()
        if (status.status == "ready") {
            _uiState.update { it.copy(message = "Connection successful!") }
        } else {
            _uiState.update { it.copy(message = "Connection failed: Server not ready") }
        }
    }

    fun downloadReport(scanId: Int, chapters: String) = viewModelScope.launch {
        _uiState.update { it.copy(isDownloadingReport = true, message = "Requesting report generation...") }
        try {
            val fileId = repository().exportScan(scanId, chapters)
            
            // Poll status
            var status = "loading"
            while (status != "ready") {
                delay(2000)
                status = repository().getExportStatus(scanId, fileId)
                _uiState.update { it.copy(message = "Generating report: $status...") }
            }
            
            _uiState.update { it.copy(message = "Downloading report...") }
            val responseBody = repository().downloadScan(scanId, fileId)
            val file = File(applicationContext.cacheDir, "scan-report-$scanId.pdf") 
            
            responseBody.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            _uiState.update { it.copy(downloadedFilePath = file.absolutePath, message = "Report downloaded successfully!") }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Failed to generate report: ${e.message}") }
        } finally {
            _uiState.update { it.copy(isDownloadingReport = false) }
        }
    }

    fun clearDownloadedFile() {
        _uiState.update { it.copy(downloadedFilePath = null) }
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

    fun updateScanName(scanId: Int, scanName: String) = runManaged("Could not update scan settings") {
        repository().updateScanName(scanId, scanName)
        loadScans()
        _uiState.update { it.copy(message = "Scan settings updated") }
    }

    fun startScan(scanId: Int) = runManaged("Could not start scan") {
        repository().startScan(scanId)
        loadScans()
        _uiState.update { it.copy(message = "Scan started") }
    }

    fun stopScan(scanId: Int) = runManaged("Could not stop scan") {
        repository().stopScan(scanId)
        loadScans()
        _uiState.update { it.copy(message = "Scan stopped") }
    }

    fun pauseScan(scanId: Int) = runManaged("Could not pause scan") {
        repository().pauseScan(scanId)
        loadScans()
        _uiState.update { it.copy(message = "Scan paused") }
    }


    fun deleteScan(scanId: Int) = runManaged("Could not delete scan") {
        repository().deleteScan(scanId)
        _uiState.update { it.copy(selectedScan = null, selectedScanDetail = null, selectedPlugin = emptyList()) }
        loadScans()
        _uiState.update { it.copy(message = "Scan deleted") }
    }

    fun loadGroups() = runManaged("Could not load groups") {
        val groups = repository().listGroups()
        _uiState.update { it.copy(groups = groups) }
    }

    fun createGroup(name: String) = runManaged("Could not create group") {
        repository().createGroup(name)
        loadGroups()
        _uiState.update { it.copy(message = "Group created") }
    }

    fun deleteGroup(groupId: Int) = runManaged("Could not delete group") {
        repository().deleteGroup(groupId)
        loadGroups()
        _uiState.update { it.copy(message = "Group deleted") }
    }

    fun loadAgentGroups() = runManaged("Could not load agent groups") {
        val groups = repository().listAgentGroups()
        _uiState.update { it.copy(agentGroups = groups) }
    }

    fun loadGroupAgents(groupId: Int) = runManaged("Could not load agents in group") {
        val agents = repository().listAgentsInGroup(groupId)
        _uiState.update { it.copy(groupAgents = agents) }
    }

    fun loadAgents() = runManaged("Could not load agents") {
        val agents = repository().listAgents()
        _uiState.update { it.copy(agents = agents) }
    }

    fun unlinkAgent(agentId: Int) = runManaged("Could not unlink agent") {
        repository().unlinkAgent(agentId)
        loadAgents()
        _uiState.update { it.copy(message = "Agent unlinked") }
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
            try {
                block()
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: defaultError) }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val store = SettingsStore(appContext)
                val vm = NessusViewModel(store, { settings ->
                    NessusRepository(
                        settingsProvider = { settings },
                        apiFactory = NessusApiFactory {
                            NessusAuth(
                                accessKey = settings.accessKey,
                                secretKey = settings.secretKey
                            )
                        }
                    )
                }, appContext)
                return vm as T
            }
        }
    }
}
