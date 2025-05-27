package com.example.plshelp.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import com.example.plshelp.android.ui.components.CategoryChip

// Mapbox Imports
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.geojson.Point
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn

// Mapbox Compose specific imports for icon handling
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import androidx.compose.ui.graphics.vector.rememberVectorPainter

// Needed for pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput


// Data class for Location Selection
data class LocationSelection(
    val type: LocationType,
    val coordinates: GeoPoint? = null
)

enum class LocationType {
    CURRENT_LOCATION,
    SELECTED_ON_MAP,
    NO_LOCATION
}

// Data class for Category Selection
data class CategorySelection(
    val selectedCategories: MutableSet<String>
)

@OptIn(MapboxExperimental::class)
@Composable
fun CreateRequestScreen(onNavigateToListings: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val user = auth.currentUser!!
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var requestCreated by rememberSaveable { mutableStateOf(false) }
    var showCategoryError by rememberSaveable { mutableStateOf(false) }

    // Location Selection State
    var locationSelection by rememberSaveable(stateSaver = locationSelectionSaver) {
        mutableStateOf(LocationSelection(LocationType.NO_LOCATION, null))
    }

    // Mapbox Map State
    val singaporePoint = Point.fromLngLat(103.8198, 1.3521)
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(singaporePoint)
            zoom(10.0) // Initial zoom level
        }
    }

    // Effect to update map camera when locationSelection.coordinates changes
    LaunchedEffect(locationSelection.coordinates) {
        locationSelection.coordinates?.let { geoPoint ->
            val point = Point.fromLngLat(geoPoint.longitude, geoPoint.latitude)
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(14.0) // Zoom in when a location is set
                    .build()
            )
        }
    }

    // Category selection state
    var categorySelection by rememberSaveable(stateSaver = categorySelectionSaver) {
        mutableStateOf(CategorySelection(mutableSetOf()))
    }

    // Track which fields have errors.
    var titleError by rememberSaveable { mutableStateOf(false) }
    var subtitleError by rememberSaveable { mutableStateOf(false) }
    var descriptionError by rememberSaveable { mutableStateOf(false) }
    var priceError by rememberSaveable { mutableStateOf(false) }
    var radiusError by rememberSaveable { mutableStateOf(false) }
    var locationError by rememberSaveable { mutableStateOf(false) }

    val firestore = FirebaseFirestore.getInstance()

    // Predefined categories.
    val availableCategories = remember {
        listOf("Urgent", "Helper", "Delivery", "Free", "Others", "Invite", "Trade", "Advice", "Event", "Study", "Borrow", "Food")
    }

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getCurrentLocation(context) { lat, lon ->
                locationSelection = LocationSelection(LocationType.CURRENT_LOCATION, GeoPoint(lat, lon))
                locationError = false
            }
        } else {
            errorMessage = "Location permission denied. Please select a location manually on the map."
            locationSelection = LocationSelection(LocationType.NO_LOCATION, null)
            locationError = true
        }
    }

    // Function to get current location, triggered by button click
    val getCurrentLocationOnClick = {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation(context) { lat, lon ->
                locationSelection = LocationSelection(LocationType.CURRENT_LOCATION, GeoPoint(lat, lon))
                locationError = false
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val createListing: () -> Unit = {
        // Update error states based on current input
        titleError = title.isBlank()
        subtitleError = subtitle.isBlank()
        descriptionError = description.isBlank()
        priceError = price.isBlank()
        radiusError = radius.isBlank()
        locationError = locationSelection.coordinates == null

        val parsedRadius = radius.toLongOrNull()
        radiusError = radius.isBlank() || parsedRadius == null || parsedRadius <= 0

        showCategoryError = categorySelection.selectedCategories.isEmpty()

        // Check if there are any errors before proceeding to write to the database
        if (!titleError && !subtitleError && !descriptionError && !priceError && !showCategoryError && !radiusError && !locationError) {
            isCreating = true
            scope.launch {
                try {
                    val userDocument = firestore.collection("users").document(user.uid).get().await()
                    val userName = userDocument.getString("name") ?: "Anonymous"

                    val currentCoordinates = locationSelection.coordinates

                    if (currentCoordinates != null) {
                        val newListing = hashMapOf(
                            "title" to title,
                            "subtitle" to subtitle,
                            "description" to description,
                            "price" to price,
                            "category" to categorySelection.selectedCategories.toList(),
                            "coord" to currentCoordinates,
                            "radius" to parsedRadius!!,
                            "ownerID" to user.uid,
                            "ownerName" to userName,
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )

                        firestore.collection("listings").add(newListing).await()

                        // Reset form and show success message
                        title = ""
                        subtitle = ""
                        description = ""
                        price = ""
                        categorySelection = CategorySelection(mutableSetOf())
                        radius = ""
                        // THIS IS THE LINE THAT WAS INCORRECTLY CHANGED
                        locationSelection = LocationSelection(LocationType.NO_LOCATION, null)
                        requestCreated = true
                        showCategoryError = false
                        errorMessage = null
                    } else {
                        Log.e("CreateRequestScreen", "Coordinates are null after validation!")
                        errorMessage = "Location is required. Please select a location on the map or use current location."
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to create request: ${e.localizedMessage}"
                    Log.e("CreateRequestScreen", "Error creating request: ${e.localizedMessage}", e)
                } finally {
                    isCreating = false
                }
            }
        }
    }

    // State to control if the outer scroll is enabled
    var parentScrollEnabled by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            // Conditionally enable/disable the parent scroll
            .verticalScroll(scrollState, enabled = parentScrollEnabled),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Create New Request", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                titleError = false
            },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            isError = titleError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (titleError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (titleError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (titleError) {
            Text("Title is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = subtitle,
            onValueChange = {
                subtitle = it
                subtitleError = false
            },
            label = { Text("Brief Summary") },
            modifier = Modifier.fillMaxWidth(),
            isError = subtitleError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (subtitleError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (subtitleError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (subtitleError) {
            Text("Subtitle is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                descriptionError = false
            },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            isError = descriptionError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (descriptionError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (descriptionError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (descriptionError) {
            Text("Description is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = price,
            onValueChange = {
                price = it
                priceError = false
            },
            label = { Text("Price") },
            modifier = Modifier.fillMaxWidth(),
            isError = priceError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (priceError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (priceError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (priceError) {
            Text("Price is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text("Category", style = MaterialTheme.typography.titleMedium)
        if (showCategoryError) {
            Text("Category is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            availableCategories.chunked(4).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowCategories.forEach { category ->
                        val isSelected = categorySelection.selectedCategories.contains(category.lowercase())

                        CategoryChip(
                            categoryString = category,
                            isSelected = isSelected,
                            onCategoryClick = { clickedCategory ->
                                val lowercasedCategory = clickedCategory.lowercase()
                                val newSelection = categorySelection.selectedCategories.toMutableSet()
                                if (lowercasedCategory in newSelection) {
                                    newSelection.remove(lowercasedCategory)
                                } else {
                                    newSelection.add(lowercasedCategory)
                                }
                                categorySelection = categorySelection.copy(selectedCategories = newSelection)
                            }
                        )
                    }
                    if (rowCategories.size < 4) {
                        for (i in rowCategories.size until 4) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Text("Location", style = MaterialTheme.typography.titleMedium)
        if (locationError) {
            Text("Location is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = getCurrentLocationOnClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use Current Location")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mapbox Map Composable
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            MapboxMap(
                modifier = Modifier
                    .fillMaxSize()
                    // Apply pointerInput directly to MapboxMap
                    // Crucially, we will NOT consume pointer events here.
                    // We only use this to detect a touch down/up to control parent scroll.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial) // Detect touch down
                            parentScrollEnabled = false // Disable parent scroll

                            // Loop through subsequent events until all fingers are up.
                            // We do NOT call consume() on any PointerInputChange here.
                            do {
                                // Just await the event, no consumption
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                // We don't need to do anything with event.changes.forEach here
                                // as we are not consuming them.
                            } while (event.changes.any { it.pressed })
                            parentScrollEnabled = true // Re-enable parent scroll
                        }
                    },
                mapViewportState = mapViewportState,
                onMapClickListener = { point ->
                    // This still works because it's a click listener, not a scroll gesture handler.
                    locationSelection = LocationSelection(
                        LocationType.SELECTED_ON_MAP,
                        GeoPoint(point.latitude(), point.longitude())
                    )
                    locationError = false
                    true
                }
            ) {
                val customLocationPainter = rememberVectorPainter(
                    image = Icons.Default.LocationOn,
                )
                val markerId = rememberIconImage(key = "location_marker_icon", painter = customLocationPainter)

                locationSelection.coordinates?.let { geoPoint ->
                    PointAnnotation(point = Point.fromLngLat(geoPoint.longitude, geoPoint.latitude)) {
                        iconImage = markerId
                    }
                }
            }
        }

        if (locationSelection.coordinates != null) {
            Text(
                text = "Selected Location: ${
                    when (locationSelection.type) {
                        LocationType.CURRENT_LOCATION -> "Current - Lat: %.4f, Lon: %.4f".format(locationSelection.coordinates!!.latitude, locationSelection.coordinates!!.longitude)
                        LocationType.SELECTED_ON_MAP -> "Map - Lat: %.4f, Lon: %.4f".format(locationSelection.coordinates!!.latitude, locationSelection.coordinates!!.longitude)
                        LocationType.NO_LOCATION -> "No location selected"
                    }
                }",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = "No location selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }


        OutlinedTextField(
            value = radius,
            onValueChange = {
                radius = it
                radiusError = false
            },
            label = { Text("Radius (meters)") },
            modifier = Modifier.fillMaxWidth(),
            isError = radiusError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (radiusError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (radiusError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (radiusError) {
            Text("Radius is required and must be a positive number", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = createListing, enabled = !isCreating) {
            if (isCreating) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text("Create Request")
            }
        }

        if (requestCreated) {
            Text(
                text = "Request created successfully!",
                color = Color.Green,
                style = MaterialTheme.typography.bodyMedium
            )
            LaunchedEffect(requestCreated) {
                if (requestCreated) {
                    kotlinx.coroutines.delay(3000)
                    requestCreated = false
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun getCurrentLocation(context: android.content.Context, onLocationResult: (Double, Double) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationResult(location.latitude, location.longitude)
                Log.d("LocationUpdate", "Fetched Last Location: ${location.latitude}, ${location.longitude}")
            } else {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { freshLocation: android.location.Location? ->
                        if (freshLocation != null) {
                            onLocationResult(freshLocation.latitude, freshLocation.longitude)
                            Log.d("LocationUpdate", "Fetched Current Location: ${freshLocation.latitude}, ${freshLocation.longitude}")
                        } else {
                            Log.e("LocationUpdate", "Could not get current location (fresh request).")
                            onLocationResult(1.3521, 103.8198) // Default to Singapore if all fails
                        }
                    }
                    .addOnFailureListener { e: Exception ->
                        Log.e("LocationUpdate", "Error getting current location: ${e.localizedMessage}")
                        onLocationResult(1.3521, 103.8198) // Default to Singapore
                    }
            }
        }.addOnFailureListener { e: Exception ->
            Log.e("LocationUpdate", "Error getting last location: ${e.localizedMessage}")
            onLocationResult(1.3521, 103.8198) // Default to Singapore
        }
    } else {
        Log.e("LocationUpdate", "Location permission not granted (getCurrentLocation function).")
        onLocationResult(1.3521, 103.8198) // Default to Singapore
    }
}

// Saver for LocationSelection
val locationSelectionSaver = run {
    val typeKey = "type"
    val latitudeKey = "latitude"
    val longitudeKey = "longitude"
    mapSaver(
        save = { locationSelection ->
            mapOf(
                typeKey to locationSelection.type.name, // Save enum as string
                latitudeKey to locationSelection.coordinates?.latitude,
                longitudeKey to locationSelection.coordinates?.longitude
            )
        },
        restore = { saved ->
            val type = LocationType.valueOf(saved[typeKey] as String) // Restore enum from string
            val latitude = saved[latitudeKey] as? Double
            val longitude = saved[longitudeKey] as? Double
            val coordinates = if (latitude != null && longitude != null) {
                GeoPoint(latitude, longitude)
            } else {
                null
            }
            LocationSelection(type, coordinates)
        }
    )
}

// Saver for CategorySelection
val categorySelectionSaver = run {
    val selectedCategoriesKey = "selectedCategories"
    mapSaver(
        save = { categorySelection ->
            mapOf(selectedCategoriesKey to categorySelection.selectedCategories.toList())
        },
        restore = { saved ->
            val savedList = saved[selectedCategoriesKey] as List<String>
            CategorySelection(savedList.toMutableSet())
        }
    )
}