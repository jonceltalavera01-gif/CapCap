package com.darkhorses.capcap.ui.theme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, paddingValues: PaddingValues, userName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Map", "Help", "Msg", "Alert", "Profile")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Map,
        Icons.Filled.Build,
        Icons.Filled.Message,
        Icons.Filled.Notifications,
        Icons.Filled.Person
    )

    var searchQuery by remember { mutableStateOf("") }
    var isLocationEnabled by remember { mutableStateOf(true) }
    var currentRoute by remember { mutableStateOf<Polyline?>(null) }
    var destinationPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // Distance Tracking State
    var isTracking by remember { mutableStateOf(false) }
    var totalDistance by remember { mutableStateOf(0.0) } // in meters
    var lastTrackedLocation by remember { mutableStateOf<Location?>(null) }

    fun checkLocationStatus() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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

    // Distance Tracker Logic
    DisposableEffect(isTracking) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (isTracking) {
                    lastTrackedLocation?.let { last ->
                        val distance = last.distanceTo(location)
                        if (distance > 2.0) { // Small threshold to avoid GPS noise
                            totalDistance += distance
                        }
                    }
                    lastTrackedLocation = location
                }
            }
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

    // Function to fetch route from OSRM
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
                            outlinePaint.color = android.graphics.Color.BLUE
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
                0 -> Homepage(paddingValues = innerPadding, userName = userName, onAlertClick = { selectedItem = 4 })
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

                                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), mapView).apply {
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
                                mapView.overlays.add(locationOverlay)
                                currentRoute?.let { mapView.overlays.add(it) }
                                mapView
                            },
                            update = { view ->
                                view.onResume()
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
                                .statusBarsPadding()
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
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
                                    focusedContainerColor = Color(0xFFEEEEEE),
                                    unfocusedContainerColor = Color(0xFFEEEEEE),
                                    disabledContainerColor = Color(0xFFEEEEEE),
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        }

                        // Controls below search bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 90.dp, start = 16.dp, end = 16.dp),
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
                                        containerColor = if (isTracking) Color.Red else Color(0xFF506D45)
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
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format("%.2f km", totalDistance / 1000.0),
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
                                // Nearby Cyclists
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "5", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DirectionsBike, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "Cyclists", fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Nearby Shops
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
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
                            containerColor = Color.Red,
                            contentColor = Color.White,
                            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                            text = { Text("SOS DISTRESS SIGNAL", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
                2 -> {
                    ShopsScreen(paddingValues = innerPadding) { destination ->
                        destinationPoint = destination
                        selectedItem = 1
                    }
                }
                3 -> {
                    MessageScreen(paddingValues = innerPadding)
                }
                4 -> {
                    AlertsScreen(paddingValues = innerPadding) { destination ->
                        destinationPoint = destination
                        selectedItem = 1
                    }
                }
                5 -> {
                    ProfileScreen(paddingValues = innerPadding, userName = userName)
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("${items[selectedItem]} Screen Coming Soon")
                    }
                }
            }

            if (showSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSheet = false },
                    sheetState = sheetState,
                    containerColor = Color.White
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
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (option == "Other") Color.Gray else Color.Red),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(option, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
