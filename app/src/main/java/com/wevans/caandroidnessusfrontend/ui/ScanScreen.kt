package com.wevans.caandroidnessusfrontend.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wevans.caandroidnessusfrontend.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// --- Navigation & Main Screens ---

@Composable
fun ScansScreen(viewModel: NessusViewModel, state: NessusUiState) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "scan_list") {
        composable("scan_list") {
            ScanListScreen(navController, viewModel, state)
        }
        composable("scan_detail/{scanId}") {
            ScanDetailScreen(navController, viewModel, state)
        }
    }
}

@Composable
fun ScanListScreen(navController: NavController, viewModel: NessusViewModel, state: NessusUiState) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredScans = if (searchQuery.isBlank()) {
        state.scans
    } else {
        state.scans.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            (it.owner?.contains(searchQuery, ignoreCase = true) == true)
        }
    }
    val groupedScans = filteredScans.groupBy { it.status }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search scans...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groupedScans.forEach { (status, scans) ->
                item {
                    Text(
                        status?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(scans, key = { it.id }) { scan ->
                    ScanCard(
                        scan = scan,
                        onCardClick = {
                            viewModel.loadScanDetails(scan)
                            navController.navigate("scan_detail/${scan.id}")
                        },
                        onStart = { viewModel.startScan(scan.id) },
                        onStop = { viewModel.stopScan(scan.id) },
                        onPause = { viewModel.pauseScan(scan.id) }
                    )
                }
            }
        }
    }
}

// --- Detail Screen & Dashboard ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailScreen(navController: NavController, viewModel: NessusViewModel, state: NessusUiState) {
    val context = LocalContext.current
    val detail = state.selectedScanDetail
    
    // UI State
    var selectedHistoryId by remember { mutableStateOf<Int?>(null) }
    var selectedHostId by remember { mutableStateOf<Int?>(null) }
    var selectedSeverities by remember { mutableStateOf(setOf(3, 4)) } // Default High & Critical
    var showExportDialog by remember { mutableStateOf(false) }

    // Effects
    LaunchedEffect(state.downloadedFilePath) {
        state.downloadedFilePath?.let { path ->
            sharePdf(context, path)
            viewModel.clearDownloadedFile()
        }
    }

    LaunchedEffect(selectedHistoryId) {
        if (selectedHistoryId != null) {
            state.selectedScan?.let {
                viewModel.loadScanDetails(it, selectedHistoryId)
                selectedHostId = null // Reset host selection on history change
            }
        }
    }
    
    LaunchedEffect(selectedHostId) {
        state.selectedScan?.let { scan ->
            viewModel.loadHostVulnerabilities(scan.id, selectedHostId, selectedHistoryId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.info?.name ?: "Scan Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedHistoryId != null && detail != null) {
                        TextButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Generate PDF", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Generate PDF")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            HistorySelector(detail?.history, selectedHistoryId) { historyId ->
                selectedHistoryId = historyId
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedHistoryId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (detail?.history.isNullOrEmpty()) "No scan history available." else "Please select a scan history.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                detail?.let { safeDetail ->
                    // Asset Filter
                    safeDetail.hosts?.let { hosts ->
                        if (hosts.isNotEmpty()) {
                            Text("Assets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                            HostFilterRow(hosts, selectedHostId) { newHostId ->
                                selectedHostId = newHostId
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Severity Filters
                    Text("Severity Filter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    SeverityFilterRow(selectedSeverities) { severity ->
                        selectedSeverities = if (selectedSeverities.contains(severity)) {
                            selectedSeverities - severity
                        } else {
                            selectedSeverities + severity
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Vulnerability List
                    val rawVulns = if (selectedHostId != null) state.hostVulnerabilities else safeDetail.vulnerabilities
                    val filteredVulns = rawVulns?.filter { it.severity in selectedSeverities }?.sortedByDescending { it.severity } ?: emptyList()
                    
                    Text("Findings (${filteredVulns.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredVulns, key = { it.pluginId }) { vuln ->
                            VulnerabilityDetailedCard(vuln) { viewModel.loadPlugin(vuln.pluginId) }
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportReportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { chapters ->
                showExportDialog = false
                state.selectedScan?.id?.let { viewModel.downloadReport(it, chapters) }
            }
        )
    }

    if (state.selectedPlugin.isNotEmpty()) {
        PluginDetailDialog(state.selectedPlugin) {
            viewModel.loadPlugin(0) // Clear plugin
        }
    }
    
    if (state.isDownloadingReport) {
        AlertDialog(
            onDismissRequest = { }, // Prevent dismissing by tapping outside
            title = { Text("Generating PDF") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(state.message ?: "Please wait...")
                }
            },
            confirmButton = {}
        )
    }
}

// --- Components ---

@Composable
fun ExportReportDialog(onDismiss: () -> Unit, onExport: (String) -> Unit) {
    val options = listOf(
        "vuln_hosts_summary" to "Executive Summary",
        "vuln_by_host" to "Vulnerabilities By Host",
        "vuln_by_plugin" to "Vulnerabilities By Plugin",
        "remediations" to "Remediations",
        "compliance_exec" to "Compliance Executive Summary",
        "compliance" to "Compliance By Host"
    )
    
    var selectedOptions by remember { mutableStateOf(setOf("vuln_hosts_summary", "vuln_by_host", "remediations")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Report Chapters") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { (apiValue, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedOptions = if (selectedOptions.contains(apiValue)) {
                                    selectedOptions - apiValue
                                } else {
                                    selectedOptions + apiValue
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedOptions.contains(apiValue),
                            onCheckedChange = null // Handled by Row click
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(selectedOptions.joinToString(";")) },
                enabled = selectedOptions.isNotEmpty()
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HostFilterRow(hosts: List<ScanHost>, selectedHostId: Int?, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedHostId == null,
            onClick = { onSelect(null) },
            label = { Text("All Assets") }
        )
        hosts.forEach { host ->
            FilterChip(
                selected = selectedHostId == host.hostId,
                onClick = { onSelect(host.hostId) },
                label = { Text(host.hostname) }
            )
        }
    }
}

@Composable
fun SeverityFilterRow(selectedSeverities: Set<Int>, onToggle: (Int) -> Unit) {
    val severities = listOf(
        Triple(4, "Critical", com.wevans.caandroidnessusfrontend.ui.theme.ErrorRed),
        Triple(3, "High", com.wevans.caandroidnessusfrontend.ui.theme.WarningYellow),
        Triple(2, "Medium", com.wevans.caandroidnessusfrontend.ui.theme.CyberCyanVariant),
        Triple(1, "Low", com.wevans.caandroidnessusfrontend.ui.theme.SuccessGreen),
        Triple(0, "Info", com.wevans.caandroidnessusfrontend.ui.theme.InfoBlue)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        severities.forEach { (severityVal, label, color) ->
            val isSelected = selectedSeverities.contains(severityVal)
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(severityVal) },
                label = { Text(label, color = if (isSelected) color else Color.Unspecified) },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = color,
                    selectedBorderColor = color
                )
            )
        }
    }
}

@Composable
fun VulnerabilityDetailedCard(vuln: ScanVulnerability, onClick: () -> Unit) {
    val color = getSeverityColor(vuln.severity)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = vuln.pluginName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CVE if available, otherwise Plugin Family
                val subtitle = vuln.cve ?: vuln.pluginFamily ?: "Unknown Family"
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = vuln.count?.toString() ?: "1",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

fun getSeverityColor(severity: Int?): Color {
    return when (severity) {
        4 -> com.wevans.caandroidnessusfrontend.ui.theme.ErrorRed
        3 -> com.wevans.caandroidnessusfrontend.ui.theme.WarningYellow
        2 -> com.wevans.caandroidnessusfrontend.ui.theme.CyberCyanVariant
        1 -> com.wevans.caandroidnessusfrontend.ui.theme.SuccessGreen
        0 -> com.wevans.caandroidnessusfrontend.ui.theme.InfoBlue
        else -> Color.Gray
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySelector(history: List<ScanHistory>?, selectedHistoryId: Int?, onHistorySelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedHistory = history?.find { it.historyId == selectedHistoryId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedHistory?.let { "Scan from ${it.creationDate.toDateString()} (${it.status})" } 
                ?: if (history.isNullOrEmpty()) "No history available" else "Select a scan history",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        if (!history.isNullOrEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                history.forEach { hist ->
                    DropdownMenuItem(
                        text = { Text("${hist.creationDate.toDateString()} - ${hist.status}") },
                        onClick = {
                            onHistorySelected(hist.historyId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PluginDetailDialog(plugin: List<PluginAttribute>, onDismiss: () -> Unit) {
    val pluginMap = plugin.associate { it.name to it.value }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginMap["plugin_name"] ?: "Plugin Details") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { PluginDetailItem("Description", pluginMap["description"], MaterialTheme.colorScheme.onSurface) }
                item { PluginDetailItem("Solution", pluginMap["solution"], MaterialTheme.colorScheme.primary) }
                item { PluginDetailItem("CVE", pluginMap["cve"], MaterialTheme.colorScheme.error) }
                item { PluginDetailItem("See Also", pluginMap["see_also"], MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun PluginDetailItem(title: String, value: String?, color: Color) {
    if (!value.isNullOrBlank()) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(value.replace("\\n", "\n"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ScanCard(
    scan: ScanSummary,
    onCardClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit
) {
    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(scan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Owner: ${scan.owner ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                when (scan.status) {
                    "running" -> {
                        Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onPause) { Text("Pause") }
                    }
                    "paused" -> Button(onClick = onStart) { Text("Resume") }
                    "completed", "canceled", "aborted" -> Button(onClick = onStart) { Text("Start") }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

fun sharePdf(context: Context, path: String) {
    val file = File(path)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Report"))
}

private fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this * 1000))
}
