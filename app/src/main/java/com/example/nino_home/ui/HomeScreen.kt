package com.example.nino_home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nino_home.BotService
import com.example.nino_home.BotStatus
import com.example.nino_home.HomeViewModel

private enum class HomeTab(val title: String) {
    Music("My Music"),
    Create("Create New"),
    Configure("Configure"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val colors = MaterialTheme.colorScheme
    var selectedTab by remember { mutableStateOf(HomeTab.Create) }
    val homeViewModel: HomeViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == HomeTab.Create) {
            homeViewModel.startBotDiscovery()
        } else {
            homeViewModel.stopBotDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose { homeViewModel.stopBotDiscovery() }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Home - ${selectedTab.title}",
                        color = colors.onPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary,
                ),
            )
        },
        bottomBar = {
            BottomMenu(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        when (selectedTab) {
            HomeTab.Music -> MusicPlaceholder(padding)
            HomeTab.Create -> CreateLanding(
                contentPadding = padding,
                onAddNewDevice = { selectedTab = HomeTab.Configure },
                discoveredBots = homeUiState.discoveredBots,
                isDiscoveringBots = homeUiState.isDiscoveringBots,
                discoveryError = homeUiState.discoveryError,
                onRefreshBots = { homeViewModel.startBotDiscovery() },
                onClearError = homeViewModel::clearDiscoveryError,
                selectedBot = homeUiState.selectedBot,
                isLoadingStatus = homeUiState.isLoadingStatus,
                botStatus = homeUiState.botStatus,
                statusError = homeUiState.statusError,
                onClearStatusError = homeViewModel::clearStatusError,
                onBotTapped = homeViewModel::fetchBotStatus,
            )
            HomeTab.Configure -> ProvisionScreen(
                showTopBar = false,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun CreateLanding(
    contentPadding: PaddingValues,
    onAddNewDevice: () -> Unit,
    discoveredBots: List<BotService>,
    isDiscoveringBots: Boolean,
    discoveryError: String?,
    onRefreshBots: () -> Unit,
    onClearError: () -> Unit,
    selectedBot: BotService?,
    isLoadingStatus: Boolean,
    botStatus: BotStatus?,
    statusError: String?,
    onClearStatusError: () -> Unit,
    onBotTapped: (BotService) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Please Setup your Device",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Bots on Home Wi-Fi",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
            )
            Button(
                onClick = onRefreshBots,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(if (isDiscoveringBots) "Scanning..." else "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (discoveredBots.isEmpty()) {
            Text(
                text = "No bot found yet. Keep bot and phone on same Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
            )
        } else {
            discoveredBots.forEach { bot ->
                BotCard(
                    bot = bot,
                    onTap = { onBotTapped(bot) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (isLoadingStatus) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Getting device status...",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
            )
        }

        botStatus?.let { status ->
            Spacer(modifier = Modifier.height(10.dp))
            StatusCard(
                selectedBot = selectedBot,
                status = status,
            )
        }

        if (statusError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearStatusError),
            )
        }

        if (discoveryError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = discoveryError,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearError),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddNewDevice,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                text = "Add New Device",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ensure your Device is charged\nor\nswitched on",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun BotCard(
    bot: BotService,
    onTap: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val localName = bot.hostName?.removeSuffix(".")
    val deviceTag = bot.txt["device"]
    val mdnsHost = localName ?: bot.host
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = bot.serviceName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = bot.serviceType,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!localName.isNullOrBlank()) {
                Text(
                    text = "$localName",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "http://${bot.host}:${bot.port}/status",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!deviceTag.isNullOrBlank()) {
                Text(
                    text = "device=$deviceTag",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    selectedBot: BotService?,
    status: BotStatus,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Device Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            selectedBot?.let {
                val mdnsHost = it.hostName?.removeSuffix(".") ?: it.host
                Text(
                    text = "${it.serviceName} ($mdnsHost)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("device_name: ${status.deviceName}", style = MaterialTheme.typography.bodyMedium)
            Text("wifi_ssid: ${status.wifiSsid}", style = MaterialTheme.typography.bodyMedium)
            Text("volume: ${status.volume}", style = MaterialTheme.typography.bodyMedium)
            Text("firmware: ${status.firmware}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MusicPlaceholder(contentPadding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "My Music",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
        )
    }
}

@Composable
private fun BottomMenu(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.secondary)
            .height(76.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomMenuItem(
            label = HomeTab.Music.title,
            selected = selectedTab == HomeTab.Music,
            onClick = { onTabSelected(HomeTab.Music) },
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(
            modifier = Modifier
                .height(36.dp)
                .width(1.dp),
            color = colors.onSecondary.copy(alpha = 0.45f),
        )
        BottomMenuItem(
            label = HomeTab.Create.title,
            selected = selectedTab == HomeTab.Create,
            onClick = { onTabSelected(HomeTab.Create) },
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(
            modifier = Modifier
                .height(36.dp)
                .width(1.dp),
            color = colors.onSecondary.copy(alpha = 0.45f),
        )
        BottomMenuItem(
            label = HomeTab.Configure.title,
            selected = selectedTab == HomeTab.Configure,
            onClick = { onTabSelected(HomeTab.Configure) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onSecondary else colors.onSecondary.copy(alpha = 0.8f),
        )
    }
}
