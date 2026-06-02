package com.wevans.caandroidnessusfrontend.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wevans.caandroidnessusfrontend.data.NessusAgent

@Composable
fun AgentsScreen(viewModel: NessusViewModel, state: NessusUiState) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "agent_groups") {
        composable("agent_groups") {
            AgentGroupListScreen(navController, viewModel, state)
        }
        composable("agent_group_detail/{groupId}/{groupName}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toIntOrNull()
            val groupName = backStackEntry.arguments?.getString("groupName") ?: "Group"
            if (groupId != null) {
                AgentListScreen(navController, viewModel, state, groupId, groupName)
            }
        }
    }
}

@Composable
fun AgentGroupListScreen(navController: NavController, viewModel: NessusViewModel, state: NessusUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var groupToDelete by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add Agent Group")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Text("Agent Groups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.agentGroups.isEmpty()) {
                item { Text("No agent groups found or access denied.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
            }
            items(state.agentGroups, key = { it.id }) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        navController.navigate("agent_group_detail/${group.id}/${group.name}")
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Agents: ${group.agentsCount}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { groupToDelete = group.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Group", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create Agent Group") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newGroupName.isNotBlank()) {
                        viewModel.createAgentGroup(newGroupName)
                        newGroupName = ""
                        showAddDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("Delete Agent Group") },
            text = { Text("Are you sure you want to delete this agent group? This will not unlink the agents themselves.") },
            confirmButton = {
                Button(onClick = {
                    groupToDelete?.let { viewModel.deleteAgentGroup(it) }
                    groupToDelete = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(navController: NavController, viewModel: NessusViewModel, state: NessusUiState, groupId: Int, groupName: String) {
    var showAddAgentDialog by remember { mutableStateOf(false) }
    var agentToUnlink by remember { mutableStateOf<NessusAgent?>(null) }
    var agentToRemoveFromGroup by remember { mutableStateOf<NessusAgent?>(null) }

    LaunchedEffect(groupId) {
        viewModel.loadGroupAgents(groupId)
        viewModel.loadAgents()
    }

    val agentsInGroup = state.groupAgents

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(groupName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddAgentDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Agent to Group")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (agentsInGroup.isEmpty()) {
                item { 
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No agents in this group.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            items(agentsInGroup) { agent ->
                AgentCard(
                    agent = agent,
                    onClick = { viewModel.selectAgent(agent) },
                    onRemoveFromGroup = { agentToRemoveFromGroup = agent },
                    onUnlink = { agentToUnlink = agent }
                )
            }
        }
    }
    
    if (state.selectedAgent != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectAgent(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AgentDetailContent(state.selectedAgent)
        }
    }

    if (showAddAgentDialog) {
        AddAgentToGroupDialog(
            allAgents = state.agents,
            currentGroupAgents = agentsInGroup,
            onDismiss = { showAddAgentDialog = false },
            onAdd = { agentId ->
                viewModel.addAgentToGroup(groupId, agentId)
                showAddAgentDialog = false
            }
        )
    }

    if (agentToRemoveFromGroup != null) {
        AlertDialog(
            onDismissRequest = { agentToRemoveFromGroup = null },
            title = { Text("Remove from Group") },
            text = { Text("Are you sure you want to remove ${agentToRemoveFromGroup?.name} from this group?") },
            confirmButton = {
                Button(onClick = {
                    agentToRemoveFromGroup?.let { viewModel.removeAgentFromGroup(groupId, it.id) }
                    agentToRemoveFromGroup = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { agentToRemoveFromGroup = null }) { Text("Cancel") }
            }
        )
    }

    if (agentToUnlink != null) {
        AlertDialog(
            onDismissRequest = { agentToUnlink = null },
            title = { Text("Unlink Agent") },
            text = { Text("Are you sure you want to completely unlink ${agentToUnlink?.name}? This action cannot be undone easily.") },
            confirmButton = {
                Button(onClick = {
                    agentToUnlink?.let { viewModel.unlinkAgent(it.id) }
                    agentToUnlink = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { agentToUnlink = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AddAgentToGroupDialog(allAgents: List<NessusAgent>, currentGroupAgents: List<NessusAgent>, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    val currentIds = currentGroupAgents.map { it.id }.toSet()
    val availableAgents = allAgents.filter { it.id !in currentIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Agent to Group") },
        text = {
            if (availableAgents.isEmpty()) {
                Text("No available agents to add.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(availableAgents) { agent ->
                        ListItem(
                            headlineContent = { Text(agent.name) },
                            supportingContent = { Text(agent.platform ?: "") },
                            leadingContent = { Icon(Icons.Default.Person, null) },
                            modifier = Modifier.clickable { onAdd(agent.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AgentCard(agent: NessusAgent, onClick: () -> Unit, onRemoveFromGroup: () -> Unit, onUnlink: () -> Unit) {
    val statusColor = getAgentStatusColor(agent.status)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(agent.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${agent.platform ?: "Unknown"} | ${agent.status ?: "offline"}", style = MaterialTheme.typography.bodySmall, color = statusColor)
            }
            IconButton(onClick = onRemoveFromGroup) {
                Icon(Icons.Default.Clear, contentDescription = "Remove from Group", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUnlink) {
                Icon(Icons.Default.Delete, contentDescription = "Unlink Agent", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AgentDetailContent(agent: NessusAgent) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(agent.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        val statusColor = getAgentStatusColor(agent.status)
        Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, statusColor)) {
            Text(agent.status?.uppercase() ?: "UNKNOWN", color = statusColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        AgentDetailRow("IP Address", agent.ip ?: "N/A")
        AgentDetailRow("Platform", agent.platform ?: "N/A")
        AgentDetailRow("Distro", agent.distro ?: "N/A")
        AgentDetailRow("Core Build", agent.coreBuild ?: "N/A")
        AgentDetailRow("UUID", agent.uuid ?: "N/A")
        AgentDetailRow("Last Connect", agent.lastConnect?.toDateString() ?: "N/A")
        AgentDetailRow("Last Scanned", agent.lastScanned?.toDateString() ?: "N/A")
        AgentDetailRow("Linked On", agent.linkedOn?.toDateString() ?: "N/A")
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AgentDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.6f))
    }
}
