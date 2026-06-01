package com.wevans.caandroidnessusfrontend.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wevans.caandroidnessusfrontend.data.NessusAgent
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AgentsScreen(viewModel: NessusViewModel, state: NessusUiState) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "agent_groups") {
        composable("agent_groups") {
            AgentGroupListScreen(navController, viewModel, state)
        }
        composable("agent_group_detail/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toIntOrNull()
            if (groupId != null) {
                AgentListScreen(navController, viewModel, state, groupId)
            }
        }
    }
}

@Composable
fun AgentGroupListScreen(navController: NavController, viewModel: NessusViewModel, state: NessusUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Agent Groups", style = MaterialTheme.typography.titleLarge) }
        if (state.agentGroups.isEmpty()) {
            item { Text("No agent groups found or access denied.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
        }
        items(state.agentGroups) { group ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    navController.navigate("agent_group_detail/${group.id}")
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(group.name, fontWeight = FontWeight.Bold)
                    Text("Agents: ${group.agentsCount}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(navController: NavController, viewModel: NessusViewModel, state: NessusUiState, groupId: Int) {
    
    LaunchedEffect(groupId) {
        viewModel.loadGroupAgents(groupId)
    }

    val agentsInGroup = state.groupAgents

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            items(agentsInGroup) { agent ->
                AgentCard(
                    agent = agent,
                    onClick = { viewModel.selectAgent(agent) },
                    onUnlink = { viewModel.unlinkAgent(agent.id) }
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
}

@Composable
fun AgentCard(agent: NessusAgent, onClick: () -> Unit, onUnlink: () -> Unit) {
    val statusColor = getAgentStatusColor(agent.status)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(2.dp, statusColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(agent.name, fontWeight = FontWeight.Bold)
                Text("${agent.platform ?: "Unknown"} | ${agent.status ?: "offline"}", style = MaterialTheme.typography.bodySmall, color = statusColor)
            }
            IconButton(onClick = onUnlink) {
                Icon(Icons.Default.Clear, "Unlink")
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

fun getAgentStatusColor(status: String?): Color {
    return when (status?.lowercase()) {
        "on", "online" -> Color(0xFF4CAF50) // Green
        "off", "offline" -> Color(0xFF9E9E9E) // Gray
        else -> Color.Gray
    }
}

private fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this * 1000))
}
