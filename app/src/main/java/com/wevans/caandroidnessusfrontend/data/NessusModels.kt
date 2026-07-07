package com.wevans.caandroidnessusfrontend.data

import com.squareup.moshi.Json

data class NessusScansResponse(
    val scans: List<ScanSummary> = emptyList(),
    val folders: List<ScanFolder> = emptyList()
)

data class ScanFolder(
    val id: Int,
    val name: String,
    val type: String? = null
)

data class ScanSummary(
    val id: Int,
    val name: String,
    @Json(name = "last_modification_date") val lastModificationDate: Long? = null,
    @Json(name = "creation_date") val creationDate: Long? = null,
    val status: String? = null,
    @Json(name = "folder_id") val folderId: Int? = null,
    @Json(name = "owner") val owner: String? = null
)

data class RemediationsContainer(
    @Json(name = "remediations") val remediations: List<Remediation> = emptyList()
)

data class NessusScanDetailResponse(
    val info: ScanInfo? = null,
    val vulnerabilities: List<ScanVulnerability>? = emptyList(),
    val remediations: RemediationsContainer? = null,
    val hosts: List<ScanHost>? = emptyList(),
    val history: List<ScanHistory>? = emptyList()
)

data class NessusScanHostResponse(
    val vulnerabilities: List<ScanVulnerability> = emptyList()
)

data class ScanHistory(
    @Json(name = "history_id") val historyId: Int,
    @Json(name = "creation_date") val creationDate: Long,
    val status: String,
    @Json(name = "owner") val owner: String? = null
)

data class ScanHost(
    @Json(name = "host_id") val hostId: Int,
    val hostname: String,
    val progress: String? = null,
    val critical: Int = 0,
    val high: Int = 0,
    val medium: Int = 0,
    val low: Int = 0,
    val info: Int = 0
)

data class ScanInfo(
    val name: String? = null,
    val status: String? = null,
    @Json(name = "policy") val policyName: String? = null,
    @Json(name = "scanner_name") val scannerName: String? = null
)

data class ScanVulnerability(
    @Json(name = "plugin_id") val pluginId: Int,
    @Json(name = "plugin_name") val pluginName: String,
    val severity: Int? = null,
    @Json(name = "plugin_family") val pluginFamily: String? = null,
    val count: Int? = null,
    val cve: String? = null
)

data class Remediation(
    val value: String? = null,
    @Json(name = "vulns") val vulnerabilityCount: Int? = null
)

data class NessusPluginResponse(
    val attributes: List<PluginAttribute> = emptyList()
)

data class PluginAttribute(
    @Json(name = "attribute_name") val name: String,
    @Json(name = "attribute_value") val value: String
)

class NessusGroupsResponse(
    val groups: List<NessusGroup> = emptyList()
)

data class NessusGroup(
    val id: String, // String to support both Int and UUID
    val name: String,
    val owner: String? = null
)

data class NessusAgentGroupsResponse(
    val groups: List<NessusAgentGroup> = emptyList()
)

data class NessusAgentGroup(
    val id: String, // Tenable.io uses UUIDs
    val name: String,
    @Json(name = "agents_count") val agentsCount: Int = 0
)

data class NessusAgentsResponse(
    val agents: List<NessusAgent> = emptyList()
)

data class NessusAgent(
    val id: Int,
    val uuid: String? = null,
    val name: String,
    val status: String? = null,
    @Json(name = "last_connect") val lastConnect: Long? = null,
    @Json(name = "last_scanned") val lastScanned: Long? = null,
    val platform: String? = null,
    val distro: String? = null,
    val ip: String? = null,
    @Json(name = "core_build") val coreBuild: String? = null,
    @Json(name = "linked_on") val linkedOn: Long? = null
)

data class ServerStatusResponse(
    val status: String? = null,
    @Json(name = "progress") val progress: String? = null
)

data class ExportScanRequest(
    val format: String = "pdf",
    val chapters: String? = null
)

data class ExportScanResponse(
    @Json(name = "file") val fileId: String
)

data class ExportStatusResponse(
    val status: String
)

data class UpdateScanSettingsRequest(
    val settings: ScanSettingsPayload
)

data class ScanSettingsPayload(
    val name: String
)

data class CreateGroupRequest(
    val name: String
)

data class CreateAgentGroupRequest(
    val name: String
)

data class Scanner(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val status: String? = null,
    val uuid: String? = null,
    @Json(name = "last_connect") val lastConnect: Long? = null
)

data class ScannersResponse(
    val scanners: List<Scanner> = emptyList()
)

// Scan Templates (for creating new scans)
data class ScanTemplate(
    val uuid: String,
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val type: String? = null,           // "local", "agent", etc.
    val cloud_only: Boolean? = null
)

data class ScanTemplatesResponse(
    val templates: List<ScanTemplate> = emptyList()
)

// Request to create a scan
data class CreateScanRequest(
    val uuid: String,
    val settings: CreateScanSettings
)

data class CreateScanSettings(
    val name: String,
    val description: String? = null,
    val targets: String? = null,           // comma-separated for network scans
    @Json(name = "agent_group_id") val agentGroupId: String? = null,  // for agent scans
    @Json(name = "scanner_id") val scannerId: String? = null,
    @Json(name = "launch_now") val launchNow: Boolean? = false,
    val folder_id: Int? = null
)

data class CreateScanResponse(
    val scan: ScanSummary? = null
)
