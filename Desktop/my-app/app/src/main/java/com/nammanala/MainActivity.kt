package com.nammanala

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDb.get(this)
        val repo = NammaNalaRepository(db)

        lifecycleScope.launch {
            repo.seedIfEmpty()
        }

        setContent {
            MaterialTheme {
                val user = rememberFirebaseUser()
                if (user == null) {
                    AuthScreen()
                } else {
                    val label = user.email?.substringBefore("@")?.takeIf { it.isNotBlank() } ?: user.uid.take(8)
                    App(
                        repo = repo,
                        signedInAs = label,
                        onSignOut = { FirebaseAuth.getInstance().signOut() },
                    )
                }
            }
        }
    }
}

private enum class Screen {
    HOME,
    BREACH,
    WATER_STATUS,
    MAINTENANCE,
    SILT,
    CANAL_MAP,
}

@Composable
private fun App(
    repo: NammaNalaRepository,
    signedInAs: String,
    onSignOut: () -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    when (screen) {
        Screen.HOME -> HomeScreen(
            signedInAs = signedInAs,
            onSignOut = onSignOut,
            onBreach = { screen = Screen.BREACH },
            onWaterStatus = { screen = Screen.WATER_STATUS },
            onMaintenance = { screen = Screen.MAINTENANCE },
            onSilt = { screen = Screen.SILT },
            onCanalMap = { screen = Screen.CANAL_MAP },
        )

        Screen.BREACH -> BreachReportScreen(repo = repo, onBack = { screen = Screen.HOME })
        Screen.WATER_STATUS -> WaterStatusScreen(repo = repo, onBack = { screen = Screen.HOME })
        Screen.MAINTENANCE -> MaintenanceScreen(repo = repo, onBack = { screen = Screen.HOME })
        Screen.SILT -> SiltAlertScreen(repo = repo, onBack = { screen = Screen.HOME })
        Screen.CANAL_MAP -> CanalMapScreen(onBack = { screen = Screen.HOME })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    signedInAs: String,
    onSignOut: () -> Unit,
    onBreach: () -> Unit,
    onWaterStatus: () -> Unit,
    onMaintenance: () -> Unit,
    onSilt: () -> Unit,
    onCanalMap: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Namma-Nala") },
                actions = {
                    Text(
                        signedInAs,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Canal Health Monitor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item { NavCard(title = "Report Breach / Leak", subtitle = "Photo + GPS → submit", onClick = onBreach) }
            item { NavCard(title = "Water Status Feed", subtitle = "Crowdsourced village updates (with timestamp)", onClick = onWaterStatus) }
            item { NavCard(title = "Maintenance Tracker", subtitle = "See scheduled cleaning for your section", onClick = onMaintenance) }
            item { NavCard(title = "Silt Alert", subtitle = "Log silt-heavy stretches reducing flow", onClick = onSilt) }
            item { NavCard(title = "Canal Map", subtitle = "Low-bandwidth canal overlay view", onClick = onCanalMap) }
        }
    }
}

@Composable
private fun NavCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreachReportScreen(repo: NammaNalaRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastFixText by remember { mutableStateOf<String?>(null) }
    var lastLocation by remember { mutableStateOf<LatLng?>(null) }
    var lastPhoto by remember { mutableStateOf<Uri?>(null) }
    var statusText by remember { mutableStateOf("Tap “Get GPS” to capture your location.") }

    val requestLocationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            statusText = if (granted) "Permission granted. Tap “Get GPS” again." else "Location permission denied."
        },
    )

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { ok ->
            lastPhoto = if (ok) pendingPhotoUri else null
            pendingPhotoUri = null
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breach Report") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 1: Take a photo", fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = {
                            val uri = createTempImageUri(context)
                            pendingPhotoUri = uri
                            takePicture.launch(uri)
                        },
                    ) { Text("Take Photo") }
                    Text(
                        if (lastPhoto == null) "No photo captured yet." else "Photo captured.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 2: Capture GPS", fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                fetchOneLocation(context) { ll ->
                                    lastLocation = ll
                                    statusText = if (ll == null) "Couldn’t get location. Try again." else "Location captured."
                                }
                            }
                        },
                    ) { Text("Get GPS") }
                    Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lastLocation != null) {
                        val nearest = remember(lastLocation) { NearestLocator.findNearestMilestone(lastLocation!!) }
                        Text(
                            "Nearest milestone: ${nearest.name} (${formatMeters(nearest.distanceMeters)})",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 3: Submit", fontWeight = FontWeight.SemiBold)
                    Button(
                        enabled = lastPhoto != null && lastLocation != null,
                        onClick = {
                            val loc = lastLocation ?: return@Button
                            val photo = lastPhoto ?: return@Button
                            val nearest = NearestLocator.findNearestMilestone(loc)
                            scope.launch {
                                repo.submitBreach(
                                    photoUri = photo.toString(),
                                    lat = loc.lat,
                                    lng = loc.lng,
                                    nearestName = nearest.name,
                                    nearestDistanceMeters = nearest.distanceMeters,
                                )
                            }
                            lastFixText = "Submitted. Nearest milestone: ${nearest.name} • ${formatMeters(nearest.distanceMeters)}"
                        },
                    ) { Text("Submit Report") }
                    if (lastFixText != null) {
                        Text(lastFixText!!, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        "Works offline: reports are stored locally (can later sync to server).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaterStatusScreen(repo: NammaNalaRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var village by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val items by repo.waterStatusFeed.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water Status") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Post an update", fontWeight = FontWeight.SemiBold)
                    SimpleTextField(
                        value = village,
                        onValueChange = { village = it },
                        placeholder = "Village name (e.g., Village X)",
                    )
                    Button(
                        enabled = village.isNotBlank() && !submitting,
                        onClick = {
                            val v = village.trim()
                            submitting = true
                            scope.launch {
                                repo.postWaterStatus(v)
                                village = ""
                                submitting = false
                            }
                        },
                    ) { Text("Post") }
                    Text(
                        "Each update shows the last updated timestamp (success criterion).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("Latest updates", fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text("No updates yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { it ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Water reached ${it.village}", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Last update: ${formatInstant(it.updatedAtEpochMs)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { scope.launch { repo.bumpWaterStatus(it.id) } }) { Text("Update") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceScreen(repo: NammaNalaRepository, onBack: () -> Unit) {
    val items by repo.maintenanceItems.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maintenance") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Scheduled cleaning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Mock schedule (simulation hint): sections update locally without network.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { it ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(it.sectionName, fontWeight = FontWeight.SemiBold)
                            Text("Next cleaning: ${it.nextCleaning}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Status: ${it.status}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiltAlertScreen(repo: NammaNalaRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    val items by repo.siltAlerts.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Silt Alerts") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Log a silt-heavy area", fontWeight = FontWeight.SemiBold)
                    SimpleTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "Example: Heavy silt near bridge, flow reduced",
                    )
                    Button(
                        enabled = note.isNotBlank(),
                        onClick = {
                            val n = note.trim()
                            scope.launch {
                                repo.addSiltAlert(n)
                                note = ""
                            }
                        },
                    ) { Text("Save") }
                }
            }

            Text("Recent", fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { it ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(it.note, fontWeight = FontWeight.Medium)
                            Text(
                                formatInstant(it.createdAtEpochMs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanalMapScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canal Map (Offline)") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Low-bandwidth map overlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "This is a lightweight overlay view (no tiles, no API key). You can replace these paths with your real canal polyline data later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CanalOverlay()
                }
            }
        }
    }
}

@Composable
private fun CanalOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Primary canal path
        val primary = Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.2f)
            quadraticTo(size.width * 0.35f, size.height * 0.05f, size.width * 0.55f, size.height * 0.25f)
            quadraticTo(size.width * 0.75f, size.height * 0.45f, size.width * 0.9f, size.height * 0.35f)
        }
        drawPath(
            path = primary,
            color = Color(0xFF1976D2),
            style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Secondary canal branches
        val branch1 = Path().apply {
            moveTo(size.width * 0.55f, size.height * 0.25f)
            quadraticTo(size.width * 0.62f, size.height * 0.38f, size.width * 0.7f, size.height * 0.65f)
        }
        drawPath(
            path = branch1,
            color = Color(0xFF43A047),
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val branch2 = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.12f)
            quadraticTo(size.width * 0.28f, size.height * 0.32f, size.width * 0.15f, size.height * 0.55f)
        }
        drawPath(
            path = branch2,
            color = Color(0xFF43A047),
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // A few "milestones"
        val pts = listOf(
            Offset(size.width * 0.1f, size.height * 0.2f),
            Offset(size.width * 0.55f, size.height * 0.25f),
            Offset(size.width * 0.9f, size.height * 0.35f),
        )
        for (p in pts) {
            drawCircle(Color(0xFFFFC107), radius = 12f, center = p)
            drawCircle(Color(0xFF6D4C41), radius = 16f, center = p, style = Stroke(width = 3f))
        }
    }
}

// ---------------------------
// Minimal UI utilities
// ---------------------------

@Composable
private fun SimpleTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    // Intentionally lightweight: no heavy input formatting, optimized for low bandwidth & low-end devices.
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
    )
}

private fun createTempImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "breach_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun fetchOneLocation(context: Context, onResult: (LatLng?) -> Unit) {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    try {
        fused.lastLocation
            .addOnSuccessListener { loc ->
                onResult(
                    if (loc == null) null else LatLng(loc.latitude, loc.longitude),
                )
            }
            .addOnFailureListener { onResult(null) }
    } catch (_: SecurityException) {
        onResult(null)
    }
}

private data class LatLng(val lat: Double, val lng: Double)

private data class Nearest(val name: String, val distanceMeters: Double)

private object NearestLocator {
    // Replace with real milestone / village coordinates for your canal network.
    private val milestones = listOf(
        "Milestone 0 (Head)" to LatLng(13.0358, 77.5970),
        "Milestone 5" to LatLng(13.0250, 77.6050),
        "Milestone 10" to LatLng(13.0150, 77.6150),
        "Milestone 15 (Tail)" to LatLng(13.0050, 77.6250),
    )

    fun findNearestMilestone(p: LatLng): Nearest {
        var best: Pair<String, Double>? = null
        for ((name, ll) in milestones) {
            val d = haversineMeters(p.lat, p.lng, ll.lat, ll.lng)
            if (best == null || d < best!!.second) best = name to d
        }
        val b = best ?: ("Unknown" to Double.NaN)
        return Nearest(name = b.first, distanceMeters = b.second)
    }
}

private fun formatMeters(m: Double): String =
    if (m.isFinite()) {
        when {
            m < 1000.0 -> "${m.toInt()} m"
            else -> String.format("%.2f km", m / 1000.0)
        }
    } else {
        "—"
    }

private fun formatInstant(epochMs: Long): String {
    val fmt = DateTimeFormatter.ofPattern("dd MMM, HH:mm")
        .withZone(ZoneId.systemDefault())
    return fmt.format(Instant.ofEpochMilli(epochMs))
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

