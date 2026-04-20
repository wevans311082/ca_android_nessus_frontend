package com.wevans.caandroidnessusfrontend.data

class NessusRepository(
    private val settingsProvider: () -> NessusSettings,
    private val apiFactory: NessusApiFactory
) {
    private fun api(): NessusApiService {
        val settings = settingsProvider()
        require(settings.baseUrl.isNotBlank()) { "Base URL is required" }
        return apiFactory.create(settings.baseUrl)
    }

    suspend fun listScans(): List<ScanSummary> = api().listScans().scans

    suspend fun getScan(scanId: Int): NessusScanDetailResponse = api().getScan(scanId)

    suspend fun getPlugin(pluginId: Int): PluginInfo? = api().getPlugin(pluginId).info

    suspend fun startScan(scanId: Int) = api().startScan(scanId)

    suspend fun stopScan(scanId: Int) = api().stopScan(scanId)

    suspend fun deleteScan(scanId: Int) = api().deleteScan(scanId)

    suspend fun updateScanName(scanId: Int, scanName: String) {
        api().updateScanSettings(scanId, UpdateScanSettingsRequest(ScanSettingsPayload(scanName)))
    }

    suspend fun listGroups(): List<NessusGroup> = api().listGroups().groups

    suspend fun createGroup(name: String) = api().createGroup(CreateGroupRequest(name.trim()))

    suspend fun deleteGroup(groupId: Int) = api().deleteGroup(groupId)

    suspend fun listAgents(): List<NessusAgent> = api().listAgents().agents

    suspend fun unlinkAgent(agentId: Int) = api().unlinkAgent(agentId = agentId)
}
