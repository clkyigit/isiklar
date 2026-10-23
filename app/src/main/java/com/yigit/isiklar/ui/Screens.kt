package com.yigit.isiklar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yigit.isiklar.model.LightDevice
import com.yigit.isiklar.model.Scene
import kotlin.math.roundToInt

@Composable
fun App(vm: MainViewModel = viewModel()) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            currentKey = vm.apiKey,
            onSave = {
                vm.saveApiKey(it)
                showSettings = false
            },
            onBack = { showSettings = false },
        )
    } else {
        HomeScreen(vm, onOpenSettings = { showSettings = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val scenes by vm.scenes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }
    var sceneToDelete by remember { mutableStateOf<Scene?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Işıklar") },
                actions = {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 12.dp).size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Yenile")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Sahneler") }
            item {
                SceneRow(
                    scenes = scenes,
                    onAllOn = { vm.allPower(true) },
                    onAllOff = { vm.allPower(false) },
                    onScene = { vm.applyScene(it) },
                    onLongScene = { sceneToDelete = it },
                    onAdd = { showSaveDialog = true },
                )
            }
            item { SectionTitle("Cihazlar") }
            if (devices.isEmpty()) {
                item { EmptyState(busy, onScan = { vm.refresh() }, onOpenSettings = onOpenSettings) }
            }
            items(devices, key = { it.id }) { d ->
                DeviceCard(
                    device = d,
                    onPower = { vm.setPower(d.id, it) },
                    onBrightness = { vm.setBrightness(d.id, it) },
                    onColor = { vm.setColor(d.id, it) },
                    onKelvin = { vm.setKelvin(d.id, it) },
                )
            }
            item {
                Text(
                    "Sahneyi silmek için üzerine uzun bas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Mevcut durumu sahne yap") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Işıkları şu an nasıl ayarladıysan o hâli kaydedilir.")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Sahne adı") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.saveScene(name)
                        showSaveDialog = false
                    },
                ) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Vazgeç") } },
        )
    }

    sceneToDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { sceneToDelete = null },
            title = { Text("Sahne silinsin mi?") },
            text = { Text("\"${s.name}\" silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteScene(s)
                    sceneToDelete = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { sceneToDelete = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SceneRow(
    scenes: List<Scene>,
    onAllOn: () -> Unit,
    onAllOff: () -> Unit,
    onScene: (Scene) -> Unit,
    onLongScene: (Scene) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SceneChip("Hepsini aç", highlight = true, onClick = onAllOn) }
        item { SceneChip("Hepsini kapat", onClick = onAllOff) }
        items(scenes, key = { it.id }) { s ->
            SceneChip(s.name, onClick = { onScene(s) }, onLongClick = { onLongScene(s) })
        }
        item { SceneChip("+ Kaydet", onClick = onAdd) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    highlight: Boolean = false,
) {
    val bg = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DeviceCard(
    device: LightDevice,
    onPower: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onColor: (com.yigit.isiklar.model.Rgb) -> Unit,
    onKelvin: (Int) -> Unit,
) {
    var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
    val on = device.state.on == true
    var bright by remember(device.id, device.state.brightness) {
        mutableFloatStateOf((device.state.brightness ?: 100).coerceIn(1, 100).toFloat())
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) device.state.displayColor() else MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${device.sku} · ${connectionLabel(device)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = on, onCheckedChange = onPower)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Parlaklık", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
                Slider(
                    value = bright,
                    onValueChange = { bright = it },
                    onValueChangeFinished = { onBrightness(bright.roundToInt()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${bright.roundToInt()}%",
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Rengi gizle" else "Renk ve beyaz ton")
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(expanded) {
                ColorControls(device, onColor = onColor, onKelvin = onKelvin)
            }
        }
    }
}

private fun connectionLabel(d: LightDevice): String = when {
    d.lanReachable -> "Yerel ağ"
    d.cloudId != null -> "Bulut"
    else -> "Ulaşılamıyor"
}

@Composable
private fun EmptyState(busy: Boolean, onScan: () -> Unit, onOpenSettings: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (busy) "Cihazlar aranıyor…" else "Henüz cihaz yok",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "1) Govee Home'da her iki cihazın Ayarlar sayfasında \"LAN Control\"ü aç.\n" +
                    "2) Telefon aynı Wi‑Fi'de olsun ve Tara'ya bas.\n" +
                    "LAN desteği olmayan cihaz için Ayarlar'dan Govee API anahtarı gir.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, enabled = !busy) { Text("Tara") }
                OutlinedButton(onClick = onOpenSettings) { Text("Ayarlar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(currentKey: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var key by rememberSaveable { mutableStateOf(currentKey) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Yerel kontrol (önerilen)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Govee Home → cihaz → Ayarlar (⚙) → \"LAN Control\" anahtarını iki cihaz için de aç. " +
                    "Telefon evdeki Wi‑Fi'deyken komutlar internete çıkmadan anında gider.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Text("Bulut yedeği", style = MaterialTheme.typography.titleMedium)
            Text(
                "Evden uzaktayken ya da LAN Control seçeneği olmayan cihazlar için. " +
                    "Govee Home → Profil → Ayarlar (⚙) → \"Apply for API Key\". Anahtar e-postayla gelir.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Govee API anahtarı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSave(key) }, modifier = Modifier.fillMaxWidth()) {
                Text("Kaydet ve cihazları tara")
            }
            HorizontalDivider()
            Text(
                "Ana ekran widget'ı: ana ekranda boş yere uzun bas → Widget'lar → Işıklar. " +
                    "Widget'ta Aç/Kapat ve ilk iki sahne bulunur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
