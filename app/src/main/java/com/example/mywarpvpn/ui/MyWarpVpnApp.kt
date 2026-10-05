package com.example.mywarpvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.mywarpvpn.R
import com.example.mywarpvpn.domain.model.ConnectionLogEntry
import com.example.mywarpvpn.domain.model.ConnectionStatus
import com.example.mywarpvpn.domain.model.VpnUiState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private enum class Destination { HOME, SETTINGS, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWarpVpnApp(
    state: VpnScreenState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImport: () -> Unit,
    onSetupWarp: () -> Unit,
    onOpenWarpTerms: () -> Unit,
    onOpenAd: () -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onFeedbackShown: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFFB9B5FF),
            secondary = Color(0xFF81D5C5),
            background = Color(0xFF11131A),
            surface = Color(0xFF191C25),
            surfaceVariant = Color(0xFF242834),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF574BE5),
            secondary = Color(0xFF138978),
            background = Color(0xFFF5F6FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFEDEEF6),
        )
    }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var showWarpConsent by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val title = when (destination) {
        Destination.HOME -> "fastspeed"
        Destination.SETTINGS -> "Settings"
        Destination.ABOUT -> "About"
    }

    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(it)
            onFeedbackShown()
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.fastspeed_logo),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
                            )
                            Text(title, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        if (destination != Destination.HOME) {
                            TextButton(onClick = { destination = if (destination == Destination.ABOUT) Destination.SETTINGS else Destination.HOME }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (destination == Destination.HOME) {
                            TextButton(onClick = { destination = Destination.SETTINGS }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        } else if (destination == Destination.SETTINGS) {
                            TextButton(onClick = { destination = Destination.ABOUT }) {
                                Icon(Icons.Default.Info, contentDescription = "About")
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            when (destination) {
                Destination.HOME -> HomeScreen(
                    state = state.vpn,
                    isBusy = state.busy,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onImport = onImport,
                    onSetupWarp = { showWarpConsent = true },
                    onOpenAd = onOpenAd,
                    modifier = Modifier.padding(padding),
                )
                Destination.SETTINGS -> SettingsScreen(
                    state = state.vpn,
                    logs = state.logs,
                    autoConnect = state.settings.autoConnectOnAppOpen,
                    onAutoConnectChanged = onAutoConnectChanged,
                    onImport = onImport,
                    onSetupWarp = { showWarpConsent = true },
                    isBusy = state.busy,
                    onOpenVpnSettings = onOpenVpnSettings,
                    modifier = Modifier.padding(padding),
                )
                Destination.ABOUT -> AboutScreen(Modifier.padding(padding))
            }
        }

        if (showWarpConsent) {
            AlertDialog(
                onDismissRequest = { showWarpConsent = false },
                title = { Text("Experimental WARP setup") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FastSpeed creates a fresh WireGuard key pair on this device. It sends Cloudflare the public key, device model, locale, and terms-acceptance time to request a WARP profile. The private key stays on this device and is stored in encrypted app storage.")
                        Text("This uses an undocumented third-party integration that Cloudflare may change or block. FastSpeed is not a Cloudflare app. Your VPN traffic will pass through Cloudflare when connected.")
                        Text("Review Cloudflare's terms before continuing.")
                        TextButton(onClick = onOpenWarpTerms) { Text("Open Cloudflare terms") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showWarpConsent = false
                        onSetupWarp()
                    }) { Text("Agree and set up") }
                },
                dismissButton = {
                    TextButton(onClick = { showWarpConsent = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: VpnUiState,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImport: () -> Unit,
    onSetupWarp: () -> Unit,
    onOpenAd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status
    val connected = status == ConnectionStatus.CONNECTED
    val pending = status == ConnectionStatus.CONNECTING || status == ConnectionStatus.DISCONNECTING
    val endpoint = state.configSummary?.endpoint
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.connectedSinceMillis) {
        while (state.connectedSinceMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val duration = state.connectedSinceMillis?.let { formatDuration(((now - it) / 1_000).coerceAtLeast(0)) } ?: "—"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StatusIcon(status)
                    Spacer(Modifier.height(18.dp))
                    Text(statusLabel(status), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(statusSubtitle(status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = if (connected || status == ConnectionStatus.CONNECTING) onDisconnect else onConnect,
                        enabled = !isBusy && status != ConnectionStatus.DISCONNECTING,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (connected) Color(0xFFB53B50) else MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            when {
                                connected -> "Disconnect"
                                status == ConnectionStatus.CONNECTING -> "Cancel connection"
                                status == ConnectionStatus.DISCONNECTING -> "Disconnecting…"
                                isBusy -> if (state.configSummary == null) "Setting up…" else "Importing…"
                                else -> "Connect"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (connected) {
                        Spacer(Modifier.height(18.dp))
                        Text("Connected for $duration", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (state.errorMessage != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        state.errorMessage,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    DetailRow("Server endpoint", endpoint ?: "Not configured")
                    DetailRow("Tunnel protocol", "WireGuard")
                    DetailRow("Session time", duration)
                }
            }
        }

        if (connected) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Traffic totals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TrafficTile("Downloaded", formatBytes(state.statistics.receivedBytes), Modifier.weight(1f))
                            TrafficTile("Uploaded", formatBytes(state.statistics.sentBytes), Modifier.weight(1f))
                        }
                        Text(
                            "Totals come from WireGuard. The app does not inspect packet contents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (connected) {
            item { SponsoredOfferCard(onOpenAd = onOpenAd) }
        }

        if (state.configSummary == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add your VPN configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Set up a per-device WARP profile or import a WireGuard client config from a provider you are authorized to use.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Button(onClick = onSetupWarp, enabled = !isBusy) { Text("Set up Cloudflare WARP") }
                        Button(onClick = onImport, enabled = !isBusy) { Text("Import configuration") }
                    }
                }
            }
        } else {
            item {
                TextButton(onClick = onImport, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Replace WireGuard configuration")
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: ConnectionStatus) {
    val color = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF168575)
        ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTING -> Color(0xFFD99026)
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier.size(92.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Security, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun TrafficTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SponsoredOfferCard(onOpenAd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Sponsored offer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Optional Adsterra offer. Tapping opens an external page; FastSpeed does not control its content.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onOpenAd, modifier = Modifier.fillMaxWidth()) {
                Text("View offer")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: VpnUiState,
    logs: List<ConnectionLogEntry>,
    autoConnect: Boolean,
    onAutoConnectChanged: (Boolean) -> Unit,
    onImport: () -> Unit,
    onSetupWarp: () -> Unit,
    isBusy: Boolean,
    onOpenVpnSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("VPN configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.configSummary == null) "No configuration imported" else "Encrypted on this device")
                    state.configSummary?.let { summary ->
                        DetailRow("Endpoint", summary.endpoint)
                        DetailRow("DNS", summary.dnsServers)
                        DetailRow("MTU", summary.mtu)
                        DetailRow("Peers", summary.peerCount.toString())
                    }
                    if (state.configSummary == null) {
                        Button(onClick = onSetupWarp, enabled = !isBusy) { Text("Set up Cloudflare WARP") }
                    }
                    TextButton(onClick = onImport) { Text(if (state.configSummary == null) "Import config" else "Replace config") }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Auto-connect on app open", fontWeight = FontWeight.Medium)
                            Text("Connect when permission is already granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoConnect, onCheckedChange = onAutoConnectChanged)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Start on boot & kill switch", fontWeight = FontWeight.Medium)
                            Text(
                                "Android manages Always-on VPN and blocking traffic without the VPN. Configure these system controls here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onOpenVpnSettings) { Text("Open Android VPN settings") }
                        }
                    }
                }
            }
        }

        item {
            Text("Connection logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Operational messages only. Configuration contents and browsing activity are never logged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (logs.isEmpty()) {
            item { Text("No connection events yet.", modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(logs.asReversed()) { entry -> LogRow(entry) }
        }
    }
}

@Composable
private fun LogRow(entry: ConnectionLogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timestampMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(entry.message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(R.drawable.fastspeed_logo),
                    contentDescription = "fastspeed logo",
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)),
                )
                Text("fastspeed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("A learning-oriented Android WireGuard client built with Kotlin and Jetpack Compose.")
                Text("WireGuard tunnel implementation: official WireGuard for Android tunnel library (Apache-2.0).")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("The connected dashboard shows an optional Adsterra sponsored link. It opens an external page only when you tap it. FastSpeed has no ad SDK or analytics SDK, does not auto-open ads, and does not record packet contents, DNS queries, or browsing history.")
                Text("Configuration files are encrypted with AES-GCM using a key held by Android Keystore and stored in app storage excluded from backup.")
                Text("WireGuard byte totals are read locally for the dashboard. Your VPN endpoint still processes the traffic you send through it, according to that provider's terms and policies.")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cloudflare WARP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("FastSpeed offers experimental WARP setup using Cloudflare's undocumented consumer registration endpoint. It creates a fresh device key and does not include a shared bootstrap key.")
                Text("This is not a Cloudflare-supported integration. Cloudflare may change or block the endpoint. FastSpeed is not affiliated with Cloudflare; review Cloudflare's terms and privacy information before connecting.")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(value, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun statusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCONNECTED -> "Disconnected"
    ConnectionStatus.CONNECTING -> "Connecting"
    ConnectionStatus.CONNECTED -> "Connected"
    ConnectionStatus.DISCONNECTING -> "Disconnecting"
    ConnectionStatus.ERROR -> "Error"
}

private fun statusSubtitle(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCONNECTED -> "Your device is using its regular network"
    ConnectionStatus.CONNECTING -> "Preparing encrypted WireGuard tunnel"
    ConnectionStatus.CONNECTED -> "VPN interface is established"
    ConnectionStatus.DISCONNECTING -> "Closing the VPN interface"
    ConnectionStatus.ERROR -> "The tunnel needs attention"
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    else "%02d:%02d".format(minutes, remainingSeconds)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    val units = listOf("kB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
