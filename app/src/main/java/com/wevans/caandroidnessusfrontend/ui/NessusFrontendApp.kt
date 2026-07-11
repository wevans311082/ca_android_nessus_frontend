package com.wevans.caandroidnessusfrontend.ui

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.wevans.caandroidnessusfrontend.BuildConfig
import com.wevans.caandroidnessusfrontend.ui.theme.NessusFrontendTheme
import kotlinx.coroutines.launch

private enum class NavItem(val label: String, val icon: ImageVector) {
    Scans("Scans", Icons.Default.Search),
    Agents("Agents", Icons.Default.Build),
    Reports("Reports", Icons.Default.Description),
    Settings("Settings", Icons.Default.Settings),
    Help("Help", Icons.Default.Info)
}

/** Walk ContextWrappers so Compose theme wrappers still resolve the host activity. */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}

/**
 * Biometric convenience unlock only (fingerprint / face).
 * Device screen-lock PIN is not used here — the app has its own backup PIN.
 */
private val APP_LOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

/** Default API endpoint for Tenable Vulnerability Management (cloud). */
const val DEFAULT_TENABLE_CLOUD_URL = "https://cloud.tenable.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NessusFrontendApp(viewModel: NessusViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentNav by rememberSaveable { mutableStateOf(NavItem.Scans) }
    // Keep the setup wizard open after connection is saved so PIN/lock step still runs
    var setupWizardActive by rememberSaveable {
        mutableStateOf(state.settings.baseUrl.isBlank())
    }

    LaunchedEffect(state.settings.baseUrl) {
        if (state.settings.baseUrl.isBlank()) {
            setupWizardActive = true
        }
    }

    // Re-lock when the whole app goes to background (not on rotation / biometric system UI).
    DisposableEffect(state.requireBiometric) {
        if (!state.requireBiometric) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.lockApp()
            }
        }
        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        processLifecycle.addObserver(observer)
        onDispose { processLifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentNav, state.settings.baseUrl, state.isUnlocked, state.requireBiometric) {
        if (state.settings.baseUrl.isBlank()) return@LaunchedEffect
        // Do not hit the API while the app is still locked
        if (state.requireBiometric && !state.isUnlocked) return@LaunchedEffect
        when (currentNav) {
            NavItem.Scans -> {
                viewModel.loadScans()
                viewModel.loadScanTemplates()
            }
            NavItem.Agents -> {
                viewModel.loadAgentGroups()
                viewModel.loadAgents()
            }
            NavItem.Reports -> viewModel.loadLocalReports()
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
                            when (currentNav) {
                                NavItem.Scans -> {
                                    IconButton(onClick = { viewModel.loadScans() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                    }
                                }
                                NavItem.Reports -> {
                                    IconButton(onClick = { viewModel.loadLocalReports() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                    }
                                }
                                else -> {}
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                val isWide = LocalConfiguration.current.screenWidthDp > 600

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = if (isWide) Alignment.Center else Alignment.TopStart
                ) {
                    val innerModifier = if (isWide) {
                        Modifier.widthIn(max = 720.dp)
                    } else Modifier.fillMaxSize()

                    Box(modifier = innerModifier) {
                        when {
                            // Full first-run wizard (stays open through connection + lock steps)
                            setupWizardActive -> {
                                SetupWizardScreen(
                                    viewModel = viewModel,
                                    state = state,
                                    onFinished = {
                                        setupWizardActive = false
                                        currentNav = NavItem.Scans
                                    }
                                )
                            }
                            state.requireBiometric && !state.isUnlocked -> {
                                LockScreen(
                                    viewModel = viewModel,
                                    onUnlock = { viewModel.unlockApp() }
                                )
                            }
                            else -> {
                                when (currentNav) {
                                    NavItem.Scans -> ScansScreen(viewModel, state)
                                    NavItem.Agents -> AgentsScreen(viewModel, state)
                                    NavItem.Reports -> ReportsScreen(viewModel, state)
                                    NavItem.Settings -> SettingsScreen(viewModel, state)
                                    NavItem.Help -> HelpScreen()
                                }
                            }
                        }
                    }

                    if (state.loading) {
                        CircularProgressIndicator()
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

/**
 * First-run setup wizard:
 * 0 Welcome → 1 API keys → 2 Connection → 3 Scanner (after successful test) → 4 App lock → 5 Done
 */
@Composable
fun SetupWizardScreen(
    viewModel: NessusViewModel,
    state: NessusUiState,
    onFinished: () -> Unit
) {
    val totalSteps = 6
    var step by rememberSaveable { mutableIntStateOf(0) }
    var baseUrl by rememberSaveable { mutableStateOf(DEFAULT_TENABLE_CLOUD_URL) }
    var accessKey by rememberSaveable { mutableStateOf("") }
    var secretKey by rememberSaveable { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var wizardSelectedScannerId by rememberSaveable {
        mutableStateOf(
            if (DEFAULT_TENABLE_CLOUD_URL.contains("cloud.tenable.com")) "null" else "1"
        )
    }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val biometricAvailable = remember {
        val activity = context.findFragmentActivity()
        activity != null &&
            BiometricManager.from(activity)
                .canAuthenticate(APP_LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Keep local selection in sync when scanners load
    LaunchedEffect(state.scanners, state.selectedScannerId, step) {
        if (step == 3) {
            val preferred = state.selectedScannerId
            if (state.scanners.any { it.id == preferred }) {
                wizardSelectedScannerId = preferred
            } else if (state.scanners.isNotEmpty()) {
                wizardSelectedScannerId = state.scanners.first().id ?: preferred
            }
        }
    }

    if (showPinDialog) {
        SetupAppPinDialog(
            title = "Set backup PIN & enable lock",
            confirmLabel = "Enable app lock",
            requireCurrentPin = false,
            onDismiss = { showPinDialog = false },
            onConfirm = { _, newPin, confirmPin ->
                val ok = viewModel.setAppPin(newPin, confirmPin, enableLock = true)
                if (ok) {
                    showPinDialog = false
                    step = 5
                }
                ok
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Progress header
        Text(
            "Setup",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            "Step ${step + 1} of $totalSteps",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Welcome to CyberAsk Scanner",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Manage Nessus and Tenable Vulnerability Management scans and agents from your phone.\n\n" +
                            "This short wizard will help you:\n" +
                            "• Connect with your API keys\n" +
                            "• Choose a scanner after a successful connection test\n" +
                            "• Optionally lock the app with a PIN and biometrics\n" +
                            "• Keep credentials encrypted on this device only",
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Get started")
                    }
                }

                1 -> {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Get your API keys",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "1. Log in to Tenable Vulnerability Management or your Nessus web UI.\n\n" +
                            "2. Open Settings → My Account → API Keys (or Users → API Keys).\n\n" +
                            "3. Generate an Access Key and Secret Key.\n\n" +
                            "Treat these like passwords — they grant full API access to your scans.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next: connection details")
                    }
                    TextButton(onClick = { step = 0 }) { Text("Back") }
                }

                2 -> {
                    Text(
                        "Connect to your server",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tenable Cloud is pre-filled. Change the URL if you use on‑premises Nessus.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = baseUrl.trim().equals(DEFAULT_TENABLE_CLOUD_URL, ignoreCase = true),
                            onClick = { baseUrl = DEFAULT_TENABLE_CLOUD_URL },
                            label = { Text("Tenable Cloud") }
                        )
                        FilterChip(
                            selected = baseUrl.contains(":8834") ||
                                (baseUrl.isNotBlank() && !baseUrl.contains("cloud.tenable.com")),
                            onClick = {
                                if (baseUrl.contains("cloud.tenable.com") || baseUrl.isBlank()) {
                                    baseUrl = "https://"
                                }
                            },
                            label = { Text("On‑prem Nessus") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API URL") },
                        supportingText = {
                            Text("Default: $DEFAULT_TENABLE_CLOUD_URL — edit for your own Nessus host")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                    )

                    if (baseUrl.isNotBlank() && !baseUrl.trim().startsWith("https://", ignoreCase = true)) {
                        Text(
                            "Prefer https:// — plain HTTP may be blocked.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = accessKey,
                        onValueChange = { accessKey = it },
                        label = { Text("Access Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = { Text("Secret Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { secretVisible = !secretVisible }) {
                                Icon(
                                    if (secretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Password, contentDescription = null) }
                    )

                    connectionError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            connectionError = null
                            val preferred =
                                if (baseUrl.contains("cloud.tenable.com", ignoreCase = true)) "null" else "1"
                            wizardSelectedScannerId = preferred
                            viewModel.saveTestAndLoadScanners(
                                baseUrl = baseUrl.trim(),
                                accessKey = accessKey.trim(),
                                secretKey = secretKey.trim(),
                                preferredScannerId = preferred
                            ) { success ->
                                if (success) {
                                    step = 3
                                } else {
                                    connectionError =
                                        "Could not connect. Check the URL, keys, and network, then try again."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.loading &&
                            baseUrl.isNotBlank() &&
                            accessKey.isNotBlank() &&
                            secretKey.isNotBlank()
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Testing connection…")
                        } else {
                            Text("Save & test connection")
                        }
                    }
                    TextButton(onClick = { step = 1 }, enabled = !state.loading) { Text("Back") }
                }

                3 -> {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Choose a scanner",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Connection succeeded. Pick the scanner this app should use for agents and scans. " +
                            "You can change this later in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Connected") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    Spacer(Modifier.height(16.dp))

                    if (state.loading && state.scanners.isEmpty()) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Loading scanners…", style = MaterialTheme.typography.bodySmall)
                    } else if (state.scanners.isEmpty()) {
                        Text(
                            "No scanners were returned by the API. You can enter a scanner ID manually " +
                                "(common values: 1 for on‑prem Nessus, null for some cloud setups).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = wizardSelectedScannerId,
                            onValueChange = { wizardSelectedScannerId = it },
                            label = { Text("Scanner ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        TextButton(onClick = { viewModel.loadScanners() }) {
                            Text("Retry load scanners")
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.scanners.forEach { scanner ->
                                val id = scanner.id ?: return@forEach
                                val selected = wizardSelectedScannerId == id
                                Card(
                                    onClick = { wizardSelectedScannerId = id },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { wizardSelectedScannerId = id }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                scanner.name ?: "Scanner $id",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                buildString {
                                                    append("ID: $id")
                                                    scanner.type?.let { append(" · $it") }
                                                    scanner.status?.let { append(" · $it") }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val id = wizardSelectedScannerId.trim().ifBlank { "1" }
                            viewModel.selectScanner(id, refreshAgents = false)
                            step = 4
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = wizardSelectedScannerId.isNotBlank()
                    ) {
                        Text("Continue with this scanner")
                    }
                    TextButton(onClick = { step = 2 }) { Text("Back") }
                }

                4 -> {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Protect this app",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "We strongly recommend enabling app lock before you start. " +
                            "API keys stay on this device, and a lock adds a barrier if the phone is left unlocked.\n\n" +
                            "• Set a 4–8 digit backup PIN (required for lock)\n" +
                            "• Use fingerprint or face when available\n" +
                            "• Unlock is required after launch and when returning from the background",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (biometricAvailable) "Biometrics available on this device"
                                else "No biometrics enrolled — PIN will be used to unlock"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (biometricAvailable) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Set PIN & enable app lock")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { step = 5 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Skip for now")
                    }
                    Text(
                        "You can enable lock anytime in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(onClick = { step = 3 }) { Text("Back") }
                }

                5 -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "You're ready",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        buildString {
                            append("Connection details are stored encrypted on this device.")
                            append("\n\nScanner: ${state.selectedScannerId}")
                            if (state.requireBiometric) {
                                append("\n\nApp lock is on — you'll use biometrics or your PIN to unlock.")
                            } else {
                                append("\n\nApp lock is off. Turn it on in Settings when you're ready.")
                            }
                            append("\n\nUse the menu to open Scans, Agents, Reports, and Settings.")
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (state.connectionStatus != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Connection: ${state.connectionStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start using CyberAsk Scanner")
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(viewModel: NessusViewModel, onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember(activity) {
        activity != null &&
            BiometricManager.from(activity)
                .canAuthenticate(APP_LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Prefer PIN UI when biometrics are missing; otherwise start with biometric + PIN option
    var showPinEntry by remember { mutableStateOf(!biometricAvailable) }
    var statusMessage by remember {
        mutableStateOf(
            if (biometricAvailable) "Use fingerprint or face, or unlock with your app PIN"
            else "Enter your app PIN to unlock"
        )
    }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var promptAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(activity, promptAttempt, showPinEntry, biometricAvailable) {
        if (activity == null || showPinEntry || !biometricAvailable) return@LaunchedEffect
        authenticateWithBiometric(
            activity = activity,
            onSuccess = onUnlock,
            onUsePin = {
                showPinEntry = true
                statusMessage = "Enter your app PIN to unlock"
            },
            onStatus = { statusMessage = it }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("App Locked", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Authenticate to access your Nessus connection",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        if (showPinEntry) {
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    if (value.length <= 8 && value.all { it.isDigit() }) {
                        pin = value
                        pinError = null
                    }
                },
                label = { Text("App PIN") },
                placeholder = { Text("4–8 digits") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = pinError != null,
                supportingText = pinError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (failedAttempts >= 5) {
                        pinError = "Too many attempts. Wait a moment, then try again."
                        return@Button
                    }
                    if (viewModel.unlockWithPin(pin)) {
                        pin = ""
                        pinError = null
                        failedAttempts = 0
                    } else {
                        failedAttempts++
                        pinError = if (failedAttempts >= 5) {
                            "Too many incorrect attempts"
                        } else {
                            "Incorrect PIN (${5 - failedAttempts} tries left)"
                        }
                        pin = ""
                    }
                },
                enabled = pin.length in 4..8 && failedAttempts < 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Unlock with PIN")
            }
            if (biometricAvailable) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    showPinEntry = false
                    pin = ""
                    pinError = null
                    promptAttempt++
                }) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use biometrics")
                }
            }
        } else {
            Button(
                onClick = { promptAttempt++ },
                enabled = activity != null,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use biometrics")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                showPinEntry = true
                statusMessage = "Enter your app PIN to unlock"
            }) {
                Text("Use app PIN instead")
            }
        }
    }
}

/**
 * System biometric prompt. Never unlocks without success.
 * Negative button / cancel routes to in-app PIN via [onUsePin].
 */
private fun authenticateWithBiometric(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onUsePin: () -> Unit,
    onStatus: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val canAuthenticate = biometricManager.canAuthenticate(APP_LOCK_AUTHENTICATORS)

    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        onUsePin()
        return
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock CyberAsk Scanner")
        .setSubtitle("Use fingerprint or face — or choose app PIN")
        .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
        .setNegativeButtonText("Use app PIN")
        .build()

    val biometricPrompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onStatus("Unlocked")
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onStatus("Not recognized. Try again or use app PIN.")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> onUsePin()
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onStatus("Biometric locked out. Use your app PIN.")
                        onUsePin()
                    }
                    else -> {
                        Toast.makeText(activity, errString, Toast.LENGTH_SHORT).show()
                        onStatus(errString.toString())
                        onUsePin()
                    }
                }
            }
        }
    )

    onStatus("Waiting for fingerprint or face…")
    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun SetupAppPinDialog(
    title: String,
    confirmLabel: String,
    requireCurrentPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String, confirmPin: String) -> Boolean
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your app PIN unlocks CyberAsk Scanner when biometrics are unavailable or locked out. " +
                        "Use 4–8 digits. This is separate from your device screen lock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (requireCurrentPin) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) currentPin = it },
                        label = { Text("Current PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                    label = { Text(if (requireCurrentPin) "New PIN" else "PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val formatError = com.wevans.caandroidnessusfrontend.data.SettingsStore.validatePinFormat(newPin)
                    if (formatError != null) {
                        error = formatError
                        return@TextButton
                    }
                    if (newPin != confirmPin) {
                        error = "PINs do not match"
                        return@TextButton
                    }
                    val ok = onConfirm(currentPin, newPin, confirmPin)
                    if (!ok) {
                        error = "Could not save PIN — check current PIN and try again"
                    }
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
    var baseUrl by remember(state.settings.baseUrl) {
        mutableStateOf(state.settings.baseUrl.ifBlank { DEFAULT_TENABLE_CLOUD_URL })
    }
    var accessKey by remember(state.settings.accessKey) { mutableStateOf(state.settings.accessKey) }
    var secretKey by remember(state.settings.secretKey) { mutableStateOf(state.settings.secretKey) }
    var scannerId by remember(state.settings.scannerId) { mutableStateOf(state.settings.scannerId) }
    var pollingInterval by remember { mutableStateOf("2000") }
    var exportTimeout by remember { mutableStateOf("300") }
    var secretKeyVisible by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }
    var showScannersDialog by remember { mutableStateOf(false) }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var setupPinToEnableLock by remember { mutableStateOf(false) }

    if (showSetupPinDialog) {
        SetupAppPinDialog(
            title = if (setupPinToEnableLock) "Set backup PIN & enable lock" else "Set backup PIN",
            confirmLabel = if (setupPinToEnableLock) "Enable lock" else "Save PIN",
            requireCurrentPin = false,
            onDismiss = {
                showSetupPinDialog = false
                setupPinToEnableLock = false
            },
            onConfirm = { _, newPin, confirmPin ->
                val ok = viewModel.setAppPin(newPin, confirmPin, enableLock = setupPinToEnableLock)
                if (ok) {
                    showSetupPinDialog = false
                    setupPinToEnableLock = false
                }
                ok
            }
        )
    }

    if (showChangePinDialog) {
        SetupAppPinDialog(
            title = "Change backup PIN",
            confirmLabel = "Change PIN",
            requireCurrentPin = true,
            onDismiss = { showChangePinDialog = false },
            onConfirm = { current, newPin, confirmPin ->
                val ok = viewModel.changeAppPin(current, newPin, confirmPin)
                if (ok) showChangePinDialog = false
                ok
            }
        )
    }

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
            supportingText = { Text("Defaults to Tenable Cloud; change for on‑prem Nessus") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
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

        OutlinedTextField(
            value = scannerId,
            onValueChange = { scannerId = it },
            label = { Text("Scanner ID (advanced: 1 or null)") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = pollingInterval,
            onValueChange = { pollingInterval = it },
            label = { Text("Polling interval (ms)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = exportTimeout,
            onValueChange = { exportTimeout = it },
            label = { Text("Export timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(8.dp))
        Text("App lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "When enabled, CyberAsk Scanner requires unlock after launch or when returning from the background. " +
                "Fingerprint/face is used when available; your app PIN is always the backup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Require app lock")
                Text(
                    if (state.hasAppPin) "Backup PIN configured" else "Backup PIN required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = state.requireBiometric,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (state.hasAppPin) {
                            viewModel.setRequireBiometric(true)
                        } else {
                            setupPinToEnableLock = true
                            showSetupPinDialog = true
                        }
                    } else {
                        viewModel.setRequireBiometric(false)
                    }
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hasAppPin) {
                OutlinedButton(
                    onClick = { showChangePinDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Change PIN")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        setupPinToEnableLock = false
                        showSetupPinDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Set backup PIN")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { 
                    viewModel.saveSettings(baseUrl, accessKey, secretKey, scannerId)
                    // TODO: persist polling/timeout to ViewModel when wired
                },
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

        if (state.connectionStatus != null || state.lastConnected != null) {
            Text(
                "Status: ${state.connectionStatus ?: "Unknown"}  •  Last: ${state.lastConnected?.let { java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(it)) } ?: "never"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.connectionStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        OutlinedButton(
            onClick = { 
                viewModel.loadScanners()
                showScannersDialog = true 
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Build, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Scanner from List")
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

    if (showScannersDialog) {
        AlertDialog(
            onDismissRequest = { showScannersDialog = false },
            title = { Text("Select Scanner") },
            text = {
                if (state.scanners.isEmpty()) {
                    Text("No scanners loaded. Make sure you are connected and have permissions.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(state.scanners) { scanner ->
                            ListItem(
                                headlineContent = { Text(scanner.name ?: scanner.id ?: "Unknown") },
                                supportingContent = { Text("ID: ${scanner.id} • ${scanner.status ?: ""}") },
                                modifier = Modifier.clickable {
                                    viewModel.selectScanner(scanner.id ?: "1")
                                    showScannersDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScannersDialog = false }) { Text("Cancel") }
            }
        )
    }
}
