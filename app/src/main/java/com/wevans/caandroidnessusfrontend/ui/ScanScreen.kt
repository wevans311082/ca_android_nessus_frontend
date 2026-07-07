package com.wevans.caandroidnessusfrontend.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import com.wevans.caandroidnessusfrontend.data.*
import java.util.*

// --- Create Scan Screens ---

@Composable
fun TemplatePickerScreen(
    navController: NavController,
    viewModel: NessusViewModel,
    state: NessusUiState
) {
    LaunchedEffect(Unit) {
        if (state.scanTemplates.isEmpty()) {
            viewModel.loadScanTemplates()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Pick Scan Template",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (state.scanTemplates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.scanTemplates) { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("create_scan/${template.uuid}")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                template.title ?: template.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            template.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            val typeLabel = when {
                                template.type?.contains("agent", ignoreCase = true) == true ||
                                template.name.contains("agent", ignoreCase = true) -> "Agent Scan"
                                else -> "Advanced / Network Scan"
                            }
                            Text(
                                typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateScanScreen(
    navController: NavController,
    viewModel: NessusViewModel,
    state: NessusUiState,
    templateUuid: String
) {
    val template = state.scanTemplates.find { it.uuid == templateUuid }

    if (template == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Template not found. Please go back and select a template again.")
            Button(onClick = { navController.popBackStack() }) { Text("Back") }
        }
        return
    }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf("") }
    var selectedAgentGroupId by remember { mutableStateOf<String?>(null) }

    val isAgent = template.let {
        it.type?.contains("agent", ignoreCase = true) == true ||
        it.name.contains("agent", ignoreCase = true)
    }

    LaunchedEffect(Unit) {
        // Ensure we start with a clean create state (previous attempt may have left the flag on after error)
        if (state.isCreatingScan) {
            viewModel.resetCreatingScan()
        }
    }

    LaunchedEffect(isAgent) {
        if (isAgent && state.agentGroups.isEmpty()) {
            viewModel.loadAgentGroups()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Create New Scan",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            "Template: ${template.title ?: template.name}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            "Using scanner: ${state.selectedScannerId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Scan Name *") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (isAgent) {
            Spacer(Modifier.height(8.dp))
            Text("Agent Group *", modifier = Modifier.padding(horizontal = 16.dp))
            if (state.agentGroups.isEmpty()) {
                Text("Loading agent groups...", modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    items(state.agentGroups) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAgentGroupId = group.id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAgentGroupId == group.id,
                                onClick = { selectedAgentGroupId = group.id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${group.name} (${group.agentsCount} agents)")
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = targets,
                onValueChange = { targets = it },
                label = { Text("Targets *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("192.168.1.0/24, 10.0.0.5") }
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.createScanFromTemplate(
                    templateUuid = templateUuid,
                    name = name,
                    description = description,
                    targets = if (isAgent) null else targets.ifBlank { null },
                    agentGroupId = selectedAgentGroupId,
                    scannerId = state.selectedScannerId
                )
                // Go back to the scan list
                navController.popBackStack("scan_list", inclusive = false)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            enabled = name.isNotBlank() &&
                    (if (isAgent) selectedAgentGroupId != null else targets.isNotBlank()) &&
                    !state.isCreatingScan
        ) {
            if (state.isCreatingScan) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Create Scan")
        }
    }
}

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
        composable("template_picker") {
            TemplatePickerScreen(navController, viewModel, state)
        }
        composable("create_scan/{templateUuid}") { backStackEntry ->
            val uuid = backStackEntry.arguments?.getString("templateUuid") ?: ""
            CreateScanScreen(navController, viewModel, state, uuid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanListScreen(navController: NavController, viewModel: NessusViewModel, state: NessusUiState) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedScans by remember { mutableStateOf(setOf<Int>()) }
    var isBulkMode by remember { mutableStateOf(false) }
    
    val filteredScans = if (searchQuery.isBlank()) {
        state.scans
    } else {
        state.scans.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            (it.owner?.contains(searchQuery, ignoreCase = true) == true)
        }
    }
    val groupedScans = filteredScans.groupBy { it.status }

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.loadScans() },
        modifier = Modifier.fillMaxSize()
    ) {
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

            if (isBulkMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { 
                        selectedScans.forEach { viewModel.startScan(it) }
                        selectedScans = emptySet()
                    }) { Text("Start Selected") }
                    Button(onClick = { 
                        selectedScans.forEach { viewModel.stopScan(it) }
                        selectedScans = emptySet()
                    }) { Text("Stop Selected") }
                    OutlinedButton(onClick = { isBulkMode = false; selectedScans = emptySet() }) { Text("Cancel") }
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = {
                        if (state.scanTemplates.isEmpty()) {
                            viewModel.loadScanTemplates()
                        }
                        navController.navigate("template_picker")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Create Scan")
                    }
                    OutlinedButton(onClick = { isBulkMode = true }) {
                        Text("Bulk Mode")
                    }
                }
            }

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isBulkMode) {
                                Checkbox(
                                    checked = selectedScans.contains(scan.id),
                                    onCheckedChange = { checked ->
                                        selectedScans = if (checked) selectedScans + scan.id else selectedScans - scan.id
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            ScanCard(
                                scan = scan,
                                onCardClick = {
                                    if (isBulkMode) {
                                        val checked = selectedScans.contains(scan.id)
                                        selectedScans = if (checked) selectedScans - scan.id else selectedScans + scan.id
                                    } else {
                                        viewModel.loadScanDetails(scan)
                                        navController.navigate("scan_detail/${scan.id}")
                                    }
                                },
                                onStart = { viewModel.startScan(scan.id) },
                                onStop = { viewModel.stopScan(scan.id) },
                                onPause = { viewModel.pauseScan(scan.id) },
                                onResume = { viewModel.resumeScan(scan.id) }
                            )
                        }
                    }
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
        // The path is set after successful generation for immediate actions below.
        // We no longer auto anything here.
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
            onExport = { chapters, format ->
                showExportDialog = false
                state.selectedScan?.let { scan ->
                    viewModel.downloadReport(scan.id, chapters, scan.name, format)
                }
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

    // Immediate actions after successful generation
    state.downloadedFilePath?.let { path ->
        if (!state.isDownloadingReport) {
            AlertDialog(
                onDismissRequest = { viewModel.clearDownloadedFile() },
                title = { Text("Report Ready") },
                text = { Text("The PDF has been saved to your local reports.") },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            viewModel.openReport(path)
                            viewModel.clearDownloadedFile()
                        }) { Text("View") }
                        TextButton(onClick = {
                            viewModel.shareReport(path)
                            viewModel.clearDownloadedFile()
                        }) { Text("Share") }
                        TextButton(onClick = { viewModel.clearDownloadedFile() }) { Text("Done") }
                    }
                }
            )
        }
    }
}

// --- Components ---

@Composable
fun ExportReportDialog(onDismiss: () -> Unit, onExport: (String, String) -> Unit) {
    val options = listOf(
        "vuln_hosts_summary" to "Executive Summary",
        "vuln_by_host" to "Vulnerabilities By Host",
        "vuln_by_plugin" to "Vulnerabilities By Plugin",
        "remediations" to "Remediations",
        "compliance_exec" to "Compliance Executive Summary",
        "compliance" to "Compliance By Host"
    )
    
    var selectedOptions by remember { mutableStateOf(setOf("vuln_hosts_summary", "vuln_by_host", "remediations")) }
    var selectedFormat by remember { mutableStateOf("pdf") }
    val formats = listOf("pdf", "csv", "html", "nessus")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Report Chapters & Format") },
        text = {
            Column {
                Text("Format:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    formats.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(fmt.uppercase()) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Chapters:", style = MaterialTheme.typography.labelMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(200.dp)) {
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
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(selectedOptions.joinToString(";"), selectedFormat) },
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
    onPause: () -> Unit,
    onResume: () -> Unit
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
                    "paused" -> Button(onClick = onResume) { Text("Resume") }
                    "completed", "canceled", "aborted" -> Button(onClick = onStart) { Text("Start") }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: NessusViewModel, state: NessusUiState) {
    val reports = state.localReports
    var renamingReport by remember { mutableStateOf<LocalReport?>(null) }
    var newName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var sortByDateDesc by remember { mutableStateOf(true) }

    val filteredSorted = reports
        .filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        .sortedWith(
            if (sortByDateDesc) compareByDescending { it.createdAt } 
            else compareBy { it.displayName.lowercase() }
        )

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search and sort
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search reports...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { sortByDateDesc = !sortByDateDesc }) {
                    Icon(
                        if (sortByDateDesc) Icons.Default.ArrowDropDown else Icons.Default.List,
                        contentDescription = if (sortByDateDesc) "Sort by name" else "Sort by date"
                    )
                }
            }

        if (filteredSorted.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (reports.isEmpty()) "No saved reports yet" else "No matching reports",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (reports.isEmpty()) {
                        Text(
                            "Generate a PDF from a scan detail screen.\nReports will be stored here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredSorted, key = { it.filePath }) { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                report.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                report.createdAt.toDateString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Spacer(Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.openReport(report.filePath) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("View")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.shareReport(report.filePath) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                                IconButton(onClick = {
                                    renamingReport = report
                                    newName = report.displayName
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                                }
                                IconButton(
                                    onClick = { viewModel.deleteLocalReport(report.filePath) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete report",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renamingReport != null) {
        AlertDialog(
            onDismissRequest = { renamingReport = null },
            title = { Text("Rename Report") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renamingReport?.let { report ->
                        if (newName.isNotBlank()) {
                            viewModel.renameLocalReport(report.filePath, newName.trim())
                        }
                    }
                    renamingReport = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renamingReport = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PdfViewerScreen(path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Zoom & pan state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Keep renderer open for the lifetime of the viewer
    var pdfRenderer by remember { mutableStateOf<android.graphics.pdf.PdfRenderer?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }

    LaunchedEffect(path) {
        try {
            val file = File(path)
            parcelFileDescriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor!!)
            totalPages = pdfRenderer!!.pageCount
            currentPageIndex = 0
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        } catch (e: Exception) {
            pageBitmap = null
        }
    }

    // Render the current page whenever page index or renderer changes
    LaunchedEffect(pdfRenderer, currentPageIndex) {
        val renderer = pdfRenderer
        if (renderer != null && currentPageIndex < renderer.pageCount) {
            try {
                val page = renderer.openPage(currentPageIndex)
                // Render at native resolution (can be large; zoom is handled by Compose layer)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pageBitmap = bitmap
                page.close()
            } catch (e: Exception) {
                pageBitmap = null
            }
        }
    }

    DisposableEffect(path) {
        onDispose {
            pageBitmap?.recycle()
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
            pdfRenderer = null
            parcelFileDescriptor = null
            pageBitmap = null
        }
    }

    fun changePage(newIndex: Int) {
        if (newIndex in 0 until totalPages && newIndex != currentPageIndex) {
            currentPageIndex = newIndex
            // Reset zoom/pan when changing pages
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Report Viewer") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                }
            },
            actions = {
                if (totalPages > 0) Text("${currentPageIndex + 1} / $totalPages")
            }
        )

        // Zoomable + pannable page area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(currentPageIndex) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                        scale = newScale

                        if (newScale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            pageBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } ?: Text("Loading PDF or no pages")
        }

        // Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (totalPages > 1) {
                Slider(
                    value = currentPageIndex.toFloat(),
                    onValueChange = { changePage(it.toInt()) },
                    valueRange = 0f..(totalPages - 1).toFloat(),
                    steps = (totalPages - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { changePage(currentPageIndex - 1) },
                    enabled = currentPageIndex > 0
                ) {
                    Text("Previous")
                }

                Button(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    enabled = scale != 1f || offsetX != 0f || offsetY != 0f
                ) {
                    Text("Reset Zoom")
                }

                Button(
                    onClick = { changePage(currentPageIndex + 1) },
                    enabled = currentPageIndex < totalPages - 1
                ) {
                    Text("Next")
                }

                Button(onClick = { viewModel.shareReport(path) }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}



}
