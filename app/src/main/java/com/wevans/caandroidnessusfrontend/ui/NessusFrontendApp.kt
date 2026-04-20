package com.wevans.caandroidnessusfrontend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class MainTab(val label: String) {
    Scans("Scans"),
    Groups("Groups"),
    Agents("Agents"),
    Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NessusFrontendApp(viewModel: NessusViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Scans) }

    LaunchedEffect(selectedTab, state.settings.baseUrl) {
        when (selectedTab) {
            MainTab.Scans -> if (state.settings.baseUrl.isNotBlank()) viewModel.loadScans()
            MainTab.Groups -> if (state.settings.baseUrl.isNotBlank()) viewModel.loadGroups()
            MainTab.Agents -> if (state.settings.baseUrl.isNotBlank()) viewModel.loadAgents()
            MainTab.Settings -> Unit
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        icon = {}
                    )
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.settings.baseUrl.isBlank() && selectedTab != MainTab.Settings) {
                    Text("Set Nessus URL and API keys in Settings to connect.")
                }
                when (selectedTab) {
                    MainTab.Scans -> ScansTab(viewModel, state)
                    MainTab.Groups -> GroupsTab(viewModel, state)
                    MainTab.Agents -> AgentsTab(viewModel, state)
                    MainTab.Settings -> SettingsTab(viewModel, state)
                }
            }
        }
    }
}

@Composable
private fun ScansTab(viewModel: NessusViewModel, state: NessusUiState) {
    var renameValue by remember(state.selectedScan?.id) { mutableStateOf(state.selectedScan?.name.orEmpty()) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Scans", style = MaterialTheme.typography.headlineSmall)
        }
        items(state.scans, key = { it.id }) { scan ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.loadScanDetails(scan) }
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(scan.name, fontWeight = FontWeight.Bold)
                    Text("Status: ${scan.status.orEmpty()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.startScan(scan.id) }) { Text("Start") }
                        Button(onClick = { viewModel.stopScan(scan.id) }) { Text("Stop") }
                        Button(onClick = { viewModel.deleteScan(scan.id) }) { Text("Delete") }
                    }
                }
            }
        }

        state.selectedScan?.let { scan ->
            item {
                Text("Edit Scan Settings", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Scan name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.updateScanName(scan.id, renameValue) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Save")
                }
            }
        }

        state.selectedScanDetail?.let { detail ->
            item {
                Text("Results", style = MaterialTheme.typography.headlineSmall)
                Text("Scan: ${detail.info?.name.orEmpty()}")
                Text("Policy: ${detail.info?.policyName.orEmpty()}")
            }
            items(detail.vulnerabilities, key = { it.pluginId }) { vuln ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(vuln.pluginName, fontWeight = FontWeight.Bold)
                        Text("Severity: ${vuln.severity ?: 0} | Count: ${vuln.count ?: 0}")
                        Button(onClick = { viewModel.loadPlugin(vuln.pluginId) }) { Text("Plugin details") }
                    }
                }
            }

            item {
                Text("Remediations", style = MaterialTheme.typography.titleLarge)
            }
            items(detail.remediations) { remediation ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(remediation.value.orEmpty())
                        Text("Affected vulns: ${remediation.vulnerabilityCount ?: 0}")
                    }
                }
            }
        }

        state.selectedPlugin?.let { plugin ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(plugin.name.orEmpty(), fontWeight = FontWeight.Bold)
                        Text("Family: ${plugin.family.orEmpty()}")
                        Text(plugin.description.orEmpty())
                        Text("Solution: ${plugin.solution.orEmpty()}")
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsTab(viewModel: NessusViewModel, state: NessusUiState) {
    var groupName by remember { mutableStateOf("") }

    Text("Groups", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = groupName,
        onValueChange = { groupName = it },
        label = { Text("New group") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(onClick = {
        if (groupName.isNotBlank()) {
            viewModel.createGroup(groupName)
            groupName = ""
        }
    }) { Text("Create Group") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.groups, key = { it.id }) { group ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(group.name, fontWeight = FontWeight.Bold)
                        Text("Owner: ${group.owner.orEmpty()}")
                    }
                    Button(onClick = { viewModel.deleteGroup(group.id) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun AgentsTab(viewModel: NessusViewModel, state: NessusUiState) {
    Text("Agents", style = MaterialTheme.typography.headlineSmall)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.agents, key = { it.id }) { agent ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(agent.name, fontWeight = FontWeight.Bold)
                        Text("Status: ${agent.status.orEmpty()}")
                    }
                    Button(onClick = { viewModel.unlinkAgent(agent.id) }) { Text("Unlink") }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(viewModel: NessusViewModel, state: NessusUiState) {
    var baseUrl by remember(state.settings.baseUrl) { mutableStateOf(state.settings.baseUrl) }
    var accessKey by remember(state.settings.accessKey) { mutableStateOf(state.settings.accessKey) }
    var secretKey by remember(state.settings.secretKey) { mutableStateOf(state.settings.secretKey) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Connection", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Nessus URL (e.g. https://host:8834)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = accessKey,
            onValueChange = { accessKey = it },
            label = { Text("Access Key") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = secretKey,
            onValueChange = { secretKey = it },
            label = { Text("Secret Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { viewModel.saveSettings(baseUrl, accessKey, secretKey) }) {
            Text("Save Settings")
        }
    }
}
