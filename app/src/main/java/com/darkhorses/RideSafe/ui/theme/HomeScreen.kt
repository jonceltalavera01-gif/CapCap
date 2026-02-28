package com.darkhorses.RideSafe.ui.theme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.URL
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, userName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showExitDialog by remember { mutableStateOf(false) }

    var selectedItem by remember { mutableIntStateOf(1) }
    val items = listOf("Home", "Map", "Services", "Alert", "Profile")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Map,
        Icons.Filled.Build,
        Icons.Filled.Notifications,
        Icons.Filled.Person
    )

    var searchQuery by remember { mutableStateOf("") }
    var isLocationEnabled by remember { mutableStateOf(true) }
    var currentRoute by remember { mutableStateOf<Polyline?>(null) }
    var destinationPoint by remember { mutableStateOf<GeoPoint?>(null) }

    val alerts = remember { mutableStateListOf<AlertItem>() }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    var isTracking by remember { mutableStateOf(false) }
    var totalDistance by remember { mutableDoubleStateOf(0.0) } 
    var lastTrackedLocation by remember { mutableStateOf<Location?>(null) }

    fun checkLocationStatus() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Handle back press
    BackHandler {
        if (selectedItem != 0) {
            selectedItem = 0
        } else {
            showExitDialog = true
        }
    }

    // Re-check location status when returning to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkLocationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        db.collection("alerts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    alerts.clear()
                    for (doc in snapshot.documents) {
                        val lat = doc.getDouble("latitude") ?: 0.0
                        val lon = doc.getDouble("longitude") ?: 0.0
                        val type = doc.getString("emergencyType") ?: "Alert"
                        val name = doc.getString("riderName") ?: "Unknown"
                        val locName = doc.getString("locationName") ?: "Resolving..."
                        alerts.add(AlertItem(
                            id = doc.id,
                            riderName = name,
                            emergencyType = type,
                            locationName = locName,
                            coordinates = GeoPoint(lat, lon),
                            time = ""
                        ))
                    }
                }
            }
    }

    fun sendEmergencySignal(type: String) {
        val myLoc = myLocationOverlay?.myLocation
        if (myLoc != null) {
            scope.launch(Dispatchers.IO) {
                // Reverse Geocode to get address
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = try {
                    val addresses = geocoder.getFromLocation(myLoc.latitude, myLoc.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        addresses[0].getAddressLine(0) ?: "Unknown Location"
                    } else "Unknown Location"
                } catch (e: Exception) {
                    "Location Error"
                }

                val alert = hashMapOf(
                    "riderName" to userName,
                    "emergencyType" to type,
                    "latitude" to myLoc.latitude,
                    "longitude" to myLoc.longitude,
                    "locationName" to address, // Save the real address
                    "timestamp" to System.currentTimeMillis(),
                    "severity" to "HIGH"
                )

                withContext(Dispatchers.Main) {
                    db.collection("alerts").add(alert)
                        .addOnSuccessListener {
                            Toast.makeText(context, "$type Alert Sent!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Failed to send alert", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        } else {
            Toast.makeText(context, "Location not available. Please wait.", Toast.LENGTH_SHORT).show()
        }
    }

    SideEffect {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            checkLocationStatus()
        }
    }

    LaunchedEffect(Unit) {
        checkLocationStatus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    DisposableEffect(isTracking) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (isTracking) {
                    lastTrackedLocation?.let { last ->
                        val distance = last.distanceTo(location)
                        if (distance > 2.0) {
                            totalDistance += distance
                        }
                    }
                    lastTrackedLocation = location
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (isTracking) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener)
            }
        }

        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    fun fetchRoute(start: GeoPoint, end: GeoPoint, map: MapView) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                val response = URL(url).readText()
                val json = JSONObject(response)
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val geometry = route.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    val points = mutableListOf<GeoPoint>()
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                    }

                    withContext(Dispatchers.Main) {
                        currentRoute?.let { map.overlays.remove(it) }
                        val polyline = Polyline().apply {
                            setPoints(points)
                            outlinePaint.color = Color.Blue.hashCode()
                            outlinePaint.strokeWidth = 10f
                        }
                        currentRoute = polyline
                        map.overlays.add(polyline)
                        map.invalidate()
                        map.controller.animateTo(start)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (!isLocationEnabled && (selectedItem == 1)) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Location Services Disabled") },
            text = { Text("Please enable GPS to see your location on the map.") },
            confirmButton = {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text("Enable GPS")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedItem) {
                0 -> Homepage(navController = navController, paddingValues = innerPadding, userName = userName)
                1 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val mapView = MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    controller.setZoom(15.0)
                                    controller.setCenter(GeoPoint(14.5995, 120.9842))
                                }

                                val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), mapView).apply {
                                    enableMyLocation()
                                    enableFollowLocation()
                                    runOnFirstFix {
                                        scope.launch {
                                            val myLoc = myLocation
                                            if (myLoc != null) {
                                                mapView.controller.animateTo(myLoc)
                                                mapView.controller.setZoom(18.0)
                                                destinationPoint?.let { dest ->
                                                    fetchRoute(myLoc, dest, mapView)
                                                }
                                            }
                                        }
                                    }
                                }

                                myLocationOverlay = overlay
                                mapView.overlays.add(overlay)

                                currentRoute?.let { mapView.overlays.add(it) }
                                mapView
                            },
                            update = { view ->
                                view.onResume()
                                
                                val markersToRemove = view.overlays.filterIsInstance<Marker>()
                                view.overlays.removeAll(markersToRemove)
                                
                                alerts.forEach { alert ->
                                    val marker = Marker(view)
                                    marker.position = alert.coordinates
                                    marker.title = "${alert.riderName}: ${alert.emergencyType}"
                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    view.overlays.add(marker)
                                }
                                view.invalidate()
                            },
                            onRelease = { view ->
                                view.onPause()
                                view.onDetach()
                            }
                        )

                        // Search Bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp, start = 16.dp, end = 16.dp)
                                .fillMaxWidth(),
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search Location") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Search Icon")
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        }

                        // Controls below search bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 70.dp, start = 16.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Track Distance Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = {
                                        isTracking = !isTracking
                                        if (isTracking) {
                                            totalDistance = 0.0
                                            lastTrackedLocation = null
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(80.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                        Text(
                                            if (isTracking) "Stop" else "Track",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (isTracking || totalDistance > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.getDefault(), "%.2f km", totalDistance / 1000.0),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }

                            // Info Boxes
                            Column(
                                modifier = Modifier.width(IntrinsicSize.Max),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "5", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "Cyclists", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "2", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "Shops", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        ExtendedFloatingActionButton(
                            onClick = { showSheet = true },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                            text = { Text("SOS DISTRESS SIGNAL", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
                2 -> ShopsScreen(
                    paddingValues = innerPadding,
                    onDirectionClick = { destination ->
                        destinationPoint = destination
                        selectedItem = 1
                    }
                )
                3 -> AlertsScreen(
                    paddingValues = innerPadding,
                    helperName = userName,
                    onViewOnMap = { destination ->
                        destinationPoint = destination
                        selectedItem = 1
                    },
                    onNavigateToHelp = { destination ->
                        destinationPoint = destination
                        selectedItem = 1
                    },
                    onImOnMyWay = { alert ->
                        Toast.makeText(context, "Notifying ${alert.riderName} you're coming!", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { alert ->
                        // Handle dismiss if needed
                    }
                )
                4 -> ProfileScreen(navController = navController, userName = userName)
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("${items[selectedItem]} Screen Coming Soon")
                    }
                }
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("Exit App") },
                    text = { Text("Are you sure you want to exit RideSafe?") },
                    confirmButton = {
                        TextButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                            Text("Exit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Select Emergency Type", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        listOf("Accident", "Medical Help", "Bike Issue", "Other").forEach { option ->
                            Button(
                                onClick = { 
                                    sendEmergencySignal(option)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (option == "Other") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(option, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
