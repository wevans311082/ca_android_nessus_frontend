package com.wevans.caandroidnessusfrontend.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wevans.caandroidnessusfrontend.BuildConfig
import com.wevans.caandroidnessusfrontend.ui.theme.NessusFrontendTheme
import kotlinx.coroutines.launch

private enum class NavItem(val label: String, val icon: ImageVector) {
    Scans("Scans", Icons.Default.Search),
    Agents("Agents", Icons.Default.Build),
    Settings("Settings", Icons.Default.Settings),
    Help("Help", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NessusFrontendApp(viewModel: NessusViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentNav by rememberSaveable { mutableStateOf(NavItem.Scans) }

    LaunchedEffect(currentNav, state.settings.baseUrl) {
        if (state.settings.baseUrl.isBlank()) return@LaunchedEffect
        when (currentNav) {
            NavItem.Scans -> viewModel.loadScans()
            NavItem.Agents -> {
                viewModel.loadAgentGroups()
                viewModel.loadAgents()
            }
            NavItem.Settings, NavItem.Help -> Unit
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearMessage()
        }
    }

    NessusFrontendTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "CyberAsk Scanner",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                    NavItem.entries.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            selected = currentNav == item,
                            onClick = {
                                currentNav = item
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentNav.label, fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            if (currentNav == NavItem.Scans) {
                                IconButton(onClick = { viewModel.loadScans() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (state.settings.baseUrl.isBlank() && currentNav != NavItem.Settings && currentNav != NavItem.Help) {
                        EmptyState("Setup Connection", "Go to Settings to configure your API connection.") {
                            currentNav = NavItem.Settings
                        }
                    } else {
                        when (currentNav) {
                            NavItem.Scans -> ScansScreen(viewModel, state)
                            NavItem.Agents -> AgentsScreen(viewModel, state)
                            NavItem.Settings -> SettingsScreen(viewModel, state)
                            NavItem.Help -> HelpScreen()
                        }
                    }

                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen() {
    val uriHandler = LocalUriHandler.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Lock, 
            contentDescription = null, 
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("CyberAsk Vulnerability Scanner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            "This application provides a secure, mobile interface to manage your vulnerability scans and agents.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = { uriHandler.openUri("https://www.cyberask.co.uk") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Visit CyberAsk Website")
        }
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { uriHandler.openUri("https://www.cyberask.co.uk/app-privacy.html") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Policy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Privacy Policy")
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp), shape = RoundedCornerShape(12.dp)) {
            Text("Go to Settings")
        }
    }
}

@Composable
fun GroupsScreen(viewModel: NessusViewModel, state: NessusUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.groups) { group ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.Bold)
                            Text("Owner: ${group.owner ?: "System"}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { viewModel.deleteGroup(group.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Group")
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create User Group") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group Name") },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newGroupName.isNotBlank()) {
                        viewModel.createGroup(newGroupName)
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
}

@Composable
fun SettingsScreen(viewModel: NessusViewModel, state: NessusUiState) {
    var baseUrl by remember(state.settings.baseUrl) { mutableStateOf(state.settings.baseUrl) }
    var accessKey by remember(state.settings.accessKey) { mutableStateOf(state.settings.accessKey) }
    var secretKey by remember(state.settings.secretKey) { mutableStateOf(state.settings.secretKey) }
    var secretKeyVisible by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("API Connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Scanner API URL") },
            placeholder = { Text("https://cloud.tenable.com") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        val trimmedUrl = baseUrl.trim()
        if (trimmedUrl.isNotBlank() && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            Text(
                "Warning: Using HTTP is insecure and may be blocked. Prefer https:// for Nessus/Tenable servers.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        OutlinedTextField(
            value = accessKey,
            onValueChange = { accessKey = it },
            label = { Text("Access Key") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = secretKey,
            onValueChange = { secretKey = it },
            label = { Text("Secret Key") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { secretKeyVisible = !secretKeyVisible }) {
                    Icon(
                        imageVector = if (secretKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (secretKeyVisible) "Hide secret key" else "Show secret key"
                    )
                }
            },
            visualTransformation = if (secretKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.saveSettings(baseUrl, accessKey, secretKey) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
            OutlinedButton(
                onClick = { viewModel.testConnection() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Test")
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text("Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        OutlinedButton(onClick = { showGroups = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Person, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Manage User Groups")
        }
    }
    
    if (showGroups) {
        AlertDialog(
            onDismissRequest = { showGroups = false },
            title = { Text("User Groups") },
            text = {
                Box(modifier = Modifier.height(400.dp)) {
                    GroupsScreen(viewModel, state)
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroups = false }) {
                    Text("Close")
                }
            }
        )
    }
}
