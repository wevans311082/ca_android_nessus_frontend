package com.wevans.caandroidnessusfrontend.data

import com.squareup.moshi.Json

data class NessusScansResponse(
    val scans: List<ScanSummary> = emptyList()
)

data class ScanSummary(
    val id: Int,
    val name: String,
    @Json(name = "last_modification_date") val lastModificationDate: Long? = null,
    @Json(name = "creation_date") val creationDate: Long? = null,
    val status: String? = null
)

data class NessusScanDetailResponse(
    val info: ScanInfo? = null,
    val vulnerabilities: List<ScanVulnerability> = emptyList(),
    val remediations: List<Remediation> = emptyList()
)

data class ScanInfo(
    val name: String? = null,
    val status: String? = null,
    @Json(name = "policy") val policyName: String? = null
)

data class ScanVulnerability(
    @Json(name = "plugin_id") val pluginId: Int,
    @Json(name = "plugin_name") val pluginName: String,
    val severity: Int? = null,
    @Json(name = "plugin_family") val pluginFamily: String? = null,
    val count: Int? = null
)

data class Remediation(
    val value: String? = null,
    @Json(name = "vulns") val vulnerabilityCount: Int? = null
)

data class NessusPluginResponse(
    val info: PluginInfo? = null
)

data class PluginInfo(
    @Json(name = "plugin_description") val description: String? = null,
    @Json(name = "plugin_solution") val solution: String? = null,
    @Json(name = "plugin_name") val name: String? = null,
    @Json(name = "plugin_family") val family: String? = null
)

data class NessusGroupsResponse(
    val groups: List<NessusGroup> = emptyList()
)

data class NessusGroup(
    val id: Int,
    val name: String,
    val owner: String? = null
)

data class NessusAgentsResponse(
    val agents: List<NessusAgent> = emptyList()
)

data class NessusAgent(
    val id: Int,
    val name: String,
    val status: String? = null,
    @Json(name = "last_connect") val lastConnect: Long? = null
)

data class StartScanRequest(
    @Json(name = "alt_targets") val altTargets: String = ""
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
