package com.wevans.caandroidnessusfrontend.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wevans.caandroidnessusfrontend.data.NessusAgent
import com.wevans.caandroidnessusfrontend.data.NessusApiFactory
import com.wevans.caandroidnessusfrontend.data.NessusAuth
import com.wevans.caandroidnessusfrontend.data.NessusGroup
import com.wevans.caandroidnessusfrontend.data.NessusRepository
import com.wevans.caandroidnessusfrontend.data.NessusScanDetailResponse
import com.wevans.caandroidnessusfrontend.data.NessusSettings
import com.wevans.caandroidnessusfrontend.data.PluginInfo
import com.wevans.caandroidnessusfrontend.data.ScanSummary
import com.wevans.caandroidnessusfrontend.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NessusUiState(
    val settings: NessusSettings = NessusSettings(),
    val scans: List<ScanSummary> = emptyList(),
    val selectedScan: ScanSummary? = null,
    val selectedScanDetail: NessusScanDetailResponse? = null,
    val selectedPlugin: PluginInfo? = null,
    val groups: List<NessusGroup> = emptyList(),
    val agents: List<NessusAgent> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

class NessusViewModel(
    private val settingsStore: SettingsStore,
    private val repositoryFactory: (NessusSettings) -> NessusRepository
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

    fun loadScans() = runManaged("Could not load scans") {
        val scans = repository().listScans()
        _uiState.update { it.copy(scans = scans) }
    }

    fun loadScanDetails(scan: ScanSummary) = runManaged("Could not load scan details") {
        val detail = repository().getScan(scan.id)
        _uiState.update { it.copy(selectedScan = scan, selectedScanDetail = detail) }
    }

    fun loadPlugin(pluginId: Int) = runManaged("Could not load plugin") {
        val plugin = repository().getPlugin(pluginId)
        _uiState.update { it.copy(selectedPlugin = plugin) }
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

    fun deleteScan(scanId: Int) = runManaged("Could not delete scan") {
        repository().deleteScan(scanId)
        _uiState.update { it.copy(selectedScan = null, selectedScanDetail = null, selectedPlugin = null) }
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

    fun loadAgents() = runManaged("Could not load agents") {
        val agents = repository().listAgents()
        _uiState.update { it.copy(agents = agents) }
    }

    fun unlinkAgent(agentId: Int) = runManaged("Could not unlink agent") {
        repository().unlinkAgent(agentId)
        loadAgents()
        _uiState.update { it.copy(message = "Agent unlinked") }
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
                val vm = NessusViewModel(store) { settings ->
                    NessusRepository(
                        settingsProvider = { settings },
                        apiFactory = NessusApiFactory {
                            NessusAuth(
                                accessKey = settings.accessKey,
                                secretKey = settings.secretKey
                            )
                        }
                    )
                }
                return vm as T
            }
        }
    }
}
