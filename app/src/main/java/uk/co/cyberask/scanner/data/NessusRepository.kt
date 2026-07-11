package uk.co.cyberask.scanner.data

import okhttp3.ResponseBody

class NessusRepository(
    private val settingsProvider: () -> NessusSettings,
    private val apiFactory: NessusApiFactory
) {
    private fun api(): NessusApiService {
        val settings = settingsProvider()
        require(settings.baseUrl.isNotBlank()) { "Base URL is required" }
        return apiFactory.create(settings.baseUrl)
    }

    suspend fun testConnection(): ServerStatusResponse = api().getServerStatus()

    suspend fun listScans(): List<ScanSummary> = api().listScans().scans

    suspend fun getScan(scanId: Int, historyId: Int? = null): NessusScanDetailResponse = api().getScan(scanId, historyId)

    suspend fun getScanHost(scanId: Int, hostId: Int, historyId: Int? = null): NessusScanHostResponse = api().getScanHost(scanId, hostId, historyId)

    suspend fun getPlugin(pluginId: Int): List<PluginAttribute> = api().getPlugin(pluginId).attributes

    suspend fun startScan(scanId: Int) = api().startScan(scanId)

    suspend fun stopScan(scanId: Int) = api().stopScan(scanId)

    suspend fun pauseScan(scanId: Int) = api().pauseScan(scanId)

    suspend fun resumeScan(scanId: Int) = api().resumeScan(scanId)

    suspend fun deleteScan(scanId: Int) = api().deleteScan(scanId)

    suspend fun updateScanName(scanId: Int, scanName: String) {
        api().updateScanSettings(scanId, UpdateScanSettingsRequest(ScanSettingsPayload(scanName)))
    }
    
    suspend fun exportScan(scanId: Int, chapters: String, format: String = "pdf"): String {
        val response = api().exportScan(scanId, ExportScanRequest(format = format, chapters = chapters))
        return response.fileId
    }
    
    suspend fun getExportStatus(scanId: Int, fileId: String): String {
        return api().getExportStatus(scanId, fileId).status
    }
    
    suspend fun downloadScan(scanId: Int, fileId: String): ResponseBody {
        return api().downloadScan(scanId, fileId)
    }

    suspend fun listGroups(): List<NessusGroup> = api().listGroups().groups

    suspend fun createGroup(name: String) = api().createGroup(CreateGroupRequest(name.trim()))

    suspend fun deleteGroup(groupId: String) = api().deleteGroup(groupId)

    suspend fun listAgentGroups(): List<NessusAgentGroup> = api().listAgentGroups(scannerId()).groups

    suspend fun createAgentGroup(name: String) = api().createAgentGroup(scannerId(), body = CreateAgentGroupRequest(name.trim()))

    suspend fun deleteAgentGroup(groupId: String) = api().deleteAgentGroup(scannerId(), groupId)

    suspend fun listAgentsInGroup(groupId: String): List<NessusAgent> = api().listAgentsInGroup(scannerId(), groupId).agents

    suspend fun addAgentToGroup(groupId: String, agentId: Int) = api().addAgentToGroup(scannerId(), groupId, agentId)

    suspend fun removeAgentFromGroup(groupId: String, agentId: Int) = api().removeAgentFromGroup(scannerId(), groupId, agentId)

    suspend fun listAgents(): List<NessusAgent> = api().listAgents(scannerId()).agents

    suspend fun unlinkAgent(agentId: Int) = api().unlinkAgent(scannerId(), agentId)

    suspend fun listScanners(): List<Scanner> = api().listScanners().scanners

    // Scan creation
    suspend fun listScanTemplates(): List<ScanTemplate> = api().listScanTemplates().templates

    suspend fun createScan(request: CreateScanRequest): CreateScanResponse =
        api().createScan(request)

    private fun scannerId(): String = settingsProvider().scannerId.ifBlank { "1" }
}
